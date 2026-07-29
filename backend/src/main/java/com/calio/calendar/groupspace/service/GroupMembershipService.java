package com.calio.calendar.groupspace.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupinvitation.service.InvitationCredentialService;
import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationRequest;
import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationResponse;
import com.calio.calendar.groupspace.controller.dto.GroupInvitationAcceptCredentialType;
import com.calio.calendar.groupspace.controller.dto.GroupMemberListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupMemberProjection;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceJoinResponse;
import com.calio.calendar.groupspace.controller.dto.TransferGroupOwnerResponse;
import com.calio.calendar.groupspace.domain.GroupJoinResult;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.domain.GroupSpaceFields;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupMembershipService {

    private static final String ACTIVE_NICKNAME_CONSTRAINT = "uk_group_member_active_nickname";

    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final InvitationCredentialService credentialService;
    private final GroupScheduleShareCleanupPort groupScheduleShareCleanupPort;
    private final List<GroupSpaceDeletionCleanup> groupDeletionCleanups;
    private final Clock clock;

    public GroupMembershipService(
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository,
            GroupInvitationRepository groupInvitationRepository,
            InvitationCredentialService credentialService,
            GroupScheduleShareCleanupPort groupScheduleShareCleanupPort,
            List<GroupSpaceDeletionCleanup> groupDeletionCleanups,
            Clock clock
    ) {
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInvitationRepository = groupInvitationRepository;
        this.credentialService = credentialService;
        this.groupScheduleShareCleanupPort = groupScheduleShareCleanupPort;
        this.groupDeletionCleanups = List.copyOf(groupDeletionCleanups);
        this.clock = clock;
    }

    @Transactional
    public AcceptGroupInvitationResponse accept(Long accountId, AcceptGroupInvitationRequest request) {
        byte[] credentialHash = credentialHash(request);
        GroupInvitation locatedInvitation = locateInvitation(request.credentialType(), credentialHash);
        GroupSpace groupSpace = lockGroupSpace(locatedInvitation.getGroupSpaceId(), true);
        List<GroupMember> lockedMembers =
                groupMemberRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpace.getId());
        GroupInvitation invitation = lockInvitation(locatedInvitation.getId(), request, credentialHash);
        validateInvitation(invitation, lockedMembers);

        GroupMember membership = findMembership(lockedMembers, accountId);
        String nickname = GroupSpaceFields.normalizeNickname(request.nickname());
        if (membership == null) {
            membership = createMembership(groupSpace, accountId, nickname);
            return response(GroupJoinResult.JOINED, groupSpace, membership, lockedMembers.size() + 1);
        }
        if (membership.getStatus() == GroupMemberStatus.ACTIVE) {
            return response(GroupJoinResult.ALREADY_MEMBER, groupSpace, membership, activeCount(lockedMembers));
        }
        requireFreshInvitation(invitation, membership);
        membership.reactivate(nickname, clock.instant());
        flushForNicknameConflict();
        return response(GroupJoinResult.REJOINED, groupSpace, membership, activeCount(lockedMembers) + 1);
    }

    @Transactional(readOnly = true)
    public GroupMemberListResponse listActiveMembers(Long accountId, Long groupSpaceId) {
        GroupSpace groupSpace = groupSpaceRepository.findById(groupSpaceId)
                .orElseThrow(GroupMembershipService::groupSpaceNotFound);
        requireActiveMembership(groupSpaceId, accountId);
        List<GroupMemberProjection> members = groupMemberRepository
                .findAllByGroupSpaceIdAndStatus(groupSpaceId, GroupMemberStatus.ACTIVE)
                .stream()
                .sorted(memberOrder(groupSpace))
                .map(member -> GroupMemberProjection.from(member, groupSpace))
                .toList();
        return new GroupMemberListResponse(members);
    }

    @Transactional
    public TransferGroupOwnerResponse transferOwnership(
            Long accountId,
            Long groupSpaceId,
            Long targetMemberId
    ) {
        GroupSpace groupSpace = lockGroupSpace(groupSpaceId, false);
        List<GroupMember> lockedMembers = lockMembers(groupSpaceId);
        GroupMember actor = requireActiveMembership(lockedMembers, accountId);
        requireOwner(groupSpace, actor);
        GroupMember target = findActiveMember(lockedMembers, targetMemberId)
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
        if (target.getId().equals(actor.getId())) {
            throw new CalioException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID);
        }

        groupSpace.transferOwnershipTo(target.getAccountId());
        return new TransferGroupOwnerResponse(
                GroupMemberProjection.from(actor, groupSpace),
                GroupMemberProjection.from(target, groupSpace)
        );
    }

    @Transactional
    public void leave(Long accountId, Long groupSpaceId) {
        GroupSpace groupSpace = lockGroupSpace(groupSpaceId, false);
        List<GroupMember> lockedMembers = lockMembers(groupSpaceId);
        GroupMember actor = requireActiveMembership(lockedMembers, accountId);
        if (actor.roleIn(groupSpace).isOwner() && activeCount(lockedMembers) > 1) {
            throw new CalioException(ErrorCode.GROUP_OWNER_TRANSFER_REQUIRED);
        }
        if (actor.roleIn(groupSpace).isOwner()) {
            deleteSoleOwnerGroup(groupSpace, lockedMembers);
            return;
        }
        deactivateMember(groupSpaceId, actor, GroupMemberStatus.LEFT);
    }

    @Transactional
    public void kick(Long accountId, Long groupSpaceId, Long targetMemberId) {
        GroupSpace groupSpace = lockGroupSpace(groupSpaceId, false);
        List<GroupMember> lockedMembers = lockMembers(groupSpaceId);
        GroupMember actor = requireActiveMembership(lockedMembers, accountId);
        requireOwner(groupSpace, actor);
        GroupMember target = findActiveMember(lockedMembers, targetMemberId)
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
        if (target.roleIn(groupSpace).isOwner()) {
            throw new CalioException(ErrorCode.GROUP_OWNER_CANNOT_BE_REMOVED);
        }
        deactivateMember(groupSpaceId, target, GroupMemberStatus.REMOVED);
    }

    private byte[] credentialHash(AcceptGroupInvitationRequest request) {
        return credentialService.hashValidated(
                request.credentialType() == GroupInvitationAcceptCredentialType.LINK_TOKEN
                        ? InvitationCredentialType.LINK_TOKEN
                        : InvitationCredentialType.CODE,
                request.credential()
        );
    }

    private GroupInvitation locateInvitation(
            GroupInvitationAcceptCredentialType credentialType,
            byte[] credentialHash
    ) {
        return (credentialType == GroupInvitationAcceptCredentialType.LINK_TOKEN
                ? groupInvitationRepository.findByLinkTokenHash(credentialHash)
                : groupInvitationRepository.findByInviteCodeHash(credentialHash))
                .orElseThrow(GroupMembershipService::invitationNotFound);
    }

    private GroupInvitation lockInvitation(
            Long invitationId,
            AcceptGroupInvitationRequest request,
            byte[] credentialHash
    ) {
        return groupInvitationRepository.findByIdAndCredentialHashForUpdate(
                        invitationId,
                        request.credentialType().name(),
                        credentialHash
                )
                .orElseThrow(GroupMembershipService::invitationNotFound);
    }

    private void validateInvitation(GroupInvitation invitation, List<GroupMember> lockedMembers) {
        if (invitation.isExpiredAt(normalize(clock.instant()))) {
            throw new CalioException(ErrorCode.GROUP_INVITATION_EXPIRED);
        }
        boolean issuerIsActive = lockedMembers.stream().anyMatch(member ->
                member.getId().equals(invitation.getCreatedByMemberId())
                        && member.getStatus() == GroupMemberStatus.ACTIVE
        );
        if (!issuerIsActive) {
            throw invitationNotFound();
        }
    }

    private GroupMember createMembership(
            GroupSpace groupSpace,
            Long accountId,
            String nickname
    ) {
        try {
            return groupMemberRepository.saveAndFlush(
                    new GroupMember(groupSpace, accountId, nickname, normalize(clock.instant()))
            );
        } catch (DataIntegrityViolationException exception) {
            throw mapNicknameConflict(exception);
        }
    }

    private void requireFreshInvitation(GroupInvitation invitation, GroupMember membership) {
        if (!normalize(invitation.getCreatedAt()).isAfter(membership.getStatusChangedAt())) {
            throw new CalioException(ErrorCode.GROUP_MEMBER_REJOIN_INVITATION_REQUIRED);
        }
    }

    private void flushForNicknameConflict() {
        try {
            groupMemberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw mapNicknameConflict(exception);
        }
    }

    private RuntimeException mapNicknameConflict(DataIntegrityViolationException exception) {
        if (containsConstraint(exception, ACTIVE_NICKNAME_CONSTRAINT)) {
            return new CalioException(ErrorCode.GROUP_MEMBER_NICKNAME_CONFLICT);
        }
        return exception;
    }

    private AcceptGroupInvitationResponse response(
            GroupJoinResult joinResult,
            GroupSpace groupSpace,
            GroupMember membership,
            int memberCount
    ) {
        GroupMemberProjection projection = GroupMemberProjection.from(membership, groupSpace);
        return new AcceptGroupInvitationResponse(
                joinResult,
                GroupSpaceJoinResponse.from(groupSpace, membership, memberCount),
                projection
        );
    }

    private void deactivateMember(
            Long groupSpaceId,
            GroupMember member,
            GroupMemberStatus inactiveStatus
    ) {
        groupScheduleShareCleanupPort.cleanupMemberShares(groupSpaceId, member.getId());
        deleteIssuerInvitations(member.getId());
        member.deactivate(inactiveStatus, clock.instant());
    }

    private void deleteSoleOwnerGroup(GroupSpace groupSpace, List<GroupMember> lockedMembers) {
        groupScheduleShareCleanupPort.cleanupGroupShares(groupSpace.getId());
        groupDeletionCleanups.forEach(cleanup -> cleanup.deleteByGroupSpaceId(groupSpace.getId()));
        groupMemberRepository.deleteAllInBatch(lockedMembers);
        groupSpaceRepository.delete(groupSpace);
    }

    private void deleteIssuerInvitations(Long memberId) {
        List<GroupInvitation> invitations =
                groupInvitationRepository.findAllByCreatedByMemberIdForUpdateOrderById(memberId);
        groupInvitationRepository.deleteAllInBatch(invitations);
    }

    private GroupSpace lockGroupSpace(Long groupSpaceId, boolean invitationScoped) {
        return groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(invitationScoped
                        ? GroupMembershipService::invitationNotFound
                        : GroupMembershipService::groupSpaceNotFound);
    }

    private List<GroupMember> lockMembers(Long groupSpaceId) {
        return groupMemberRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpaceId);
    }

    private GroupMember requireActiveMembership(Long groupSpaceId, Long accountId) {
        return groupMemberRepository.findByGroupSpaceIdAndAccountIdAndStatus(
                        groupSpaceId,
                        accountId,
                        GroupMemberStatus.ACTIVE
                )
                .orElseThrow(GroupMembershipService::groupSpaceNotFound);
    }

    private GroupMember requireActiveMembership(List<GroupMember> members, Long accountId) {
        GroupMember member = findMembership(members, accountId);
        if (member == null || member.getStatus() != GroupMemberStatus.ACTIVE) {
            throw groupSpaceNotFound();
        }
        return member;
    }

    private GroupMember findMembership(List<GroupMember> members, Long accountId) {
        return members.stream()
                .filter(member -> member.getAccountId().equals(accountId))
                .findFirst()
                .orElse(null);
    }

    private java.util.Optional<GroupMember> findActiveMember(List<GroupMember> members, Long memberId) {
        return members.stream()
                .filter(member -> member.getId().equals(memberId))
                .filter(member -> member.getStatus() == GroupMemberStatus.ACTIVE)
                .findFirst();
    }

    private Comparator<GroupMember> memberOrder(GroupSpace groupSpace) {
        return Comparator.comparing((GroupMember member) -> !member.roleIn(groupSpace).isOwner())
                .thenComparing(GroupMember::getStatusChangedAt)
                .thenComparing(GroupMember::getId);
    }

    private int activeCount(List<GroupMember> members) {
        return (int) members.stream()
                .filter(member -> member.getStatus() == GroupMemberStatus.ACTIVE)
                .count();
    }

    private void requireOwner(GroupSpace groupSpace, GroupMember member) {
        if (!member.roleIn(groupSpace).isOwner()) {
            throw new CalioException(ErrorCode.GROUP_OWNER_REQUIRED);
        }
    }

    private boolean containsConstraint(Throwable throwable, String constraintName) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Instant normalize(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }

    private static CalioException invitationNotFound() {
        return new CalioException(ErrorCode.GROUP_INVITATION_NOT_FOUND);
    }
}
