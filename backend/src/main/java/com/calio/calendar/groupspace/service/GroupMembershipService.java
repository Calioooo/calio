package com.calio.calendar.groupspace.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.calio.calendar.groupinvitation.service.GroupInvitationCommandService;
import com.calio.calendar.groupinvitation.service.GroupInvitationQueryService;
import com.calio.calendar.groupinvitation.service.InvitationCredentialService;
import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationRequest;
import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationResponse;
import com.calio.calendar.groupspace.controller.dto.GroupInvitationAcceptCredentialType;
import com.calio.calendar.groupspace.controller.dto.GroupMemberListResponse;
import com.calio.calendar.groupspace.controller.dto.TransferGroupOwnerResponse;
import com.calio.calendar.groupspace.domain.GroupJoinResult;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.domain.GroupSpaceFields;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupMembershipService {

    private final GroupMembershipQueryService queryService;
    private final GroupMembershipCommandService commandService;
    private final GroupSpaceCommandService groupSpaceCommandService;
    private final GroupInvitationQueryService invitationQueryService;
    private final GroupInvitationCommandService invitationCommandService;
    private final InvitationCredentialService credentialService;
    private final GroupScheduleShareCleanupPort groupScheduleShareCleanupPort;
    private final Clock clock;

    public GroupMembershipService(
            GroupMembershipQueryService queryService,
            GroupMembershipCommandService commandService,
            GroupSpaceCommandService groupSpaceCommandService,
            GroupInvitationQueryService invitationQueryService,
            GroupInvitationCommandService invitationCommandService,
            InvitationCredentialService credentialService,
            GroupScheduleShareCleanupPort groupScheduleShareCleanupPort,
            Clock clock
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.groupSpaceCommandService = groupSpaceCommandService;
        this.invitationQueryService = invitationQueryService;
        this.invitationCommandService = invitationCommandService;
        this.credentialService = credentialService;
        this.groupScheduleShareCleanupPort = groupScheduleShareCleanupPort;
        this.clock = clock;
    }

    @Transactional
    public AcceptGroupInvitationResponse accept(Long accountId, AcceptGroupInvitationRequest request) {
        byte[] credentialHash = credentialHash(request);
        GroupInvitation locatedInvitation = locateInvitation(request.credentialType(), credentialHash);
        GroupSpace groupSpace = commandService.lockGroupSpaceForInvitation(
                locatedInvitation.getGroupSpaceId()
        );
        List<GroupMember> lockedMembers = commandService.lockMembers(groupSpace.getId());
        GroupInvitation invitation = invitationCommandService.lockInvitation(
                locatedInvitation.getId(),
                invitationCredentialType(request.credentialType()),
                credentialHash
        );
        Instant now = clock.instant();
        validateInvitation(invitation, lockedMembers, now);

        GroupMember membership = findMembership(lockedMembers, accountId);
        String nickname = GroupSpaceFields.normalizeNickname(request.nickname());
        if (membership == null) {
            requireAvailableNickname(groupSpace.getId(), nickname, null);
            membership = createMembership(groupSpace, accountId, nickname, now);
            return AcceptGroupInvitationResponse.from(
                    GroupJoinResult.JOINED,
                    groupSpace,
                    membership,
                    activeCount(lockedMembers) + 1
            );
        }
        if (membership.getStatus() == GroupMemberStatus.ACTIVE) {
            return AcceptGroupInvitationResponse.from(
                    GroupJoinResult.ALREADY_MEMBER,
                    groupSpace,
                    membership,
                    activeCount(lockedMembers)
            );
        }
        requireFreshInvitation(invitation, membership);
        requireAvailableNickname(groupSpace.getId(), nickname, membership.getId());
        commandService.changeToActive(membership, nickname, now);
        return AcceptGroupInvitationResponse.from(
                GroupJoinResult.REJOINED,
                groupSpace,
                membership,
                activeCount(lockedMembers) + 1
        );
    }

    @Transactional(readOnly = true)
    public GroupMemberListResponse listActiveMembers(Long accountId, Long groupSpaceId) {
        GroupSpace groupSpace = queryService.getGroupSpace(groupSpaceId);
        requireActiveMembership(groupSpaceId, accountId);
        List<GroupMember> activeMembers = queryService.listActiveMembers(groupSpaceId)
                .stream()
                .sorted(memberOrder(groupSpace))
                .toList();
        return GroupMemberListResponse.from(activeMembers, groupSpace);
    }

    @Transactional
    public TransferGroupOwnerResponse transferOwnership(
            Long accountId,
            Long groupSpaceId,
            Long targetMemberId
    ) {
        GroupSpace groupSpace = commandService.lockGroupSpace(groupSpaceId);
        List<GroupMember> lockedMembers = commandService.lockMembers(groupSpaceId);
        GroupMember actor = requireActiveMembership(lockedMembers, accountId);
        requireOwner(groupSpace, actor);
        GroupMember target = findActiveMember(lockedMembers, targetMemberId)
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
        if (target.getId().equals(actor.getId())) {
            throw new CalioException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID);
        }

        commandService.changeOwnership(groupSpace, target.getAccountId());
        return TransferGroupOwnerResponse.from(groupSpace, actor, target);
    }

    @Transactional
    public void leave(Long accountId, Long groupSpaceId) {
        GroupSpace groupSpace = commandService.lockGroupSpace(groupSpaceId);
        List<GroupMember> lockedMembers = commandService.lockMembers(groupSpaceId);
        GroupMember actor = requireActiveMembership(lockedMembers, accountId);
        Instant now = clock.instant();
        if (actor.roleIn(groupSpace).isOwner() && activeCount(lockedMembers) > 1) {
            throw new CalioException(ErrorCode.GROUP_OWNER_TRANSFER_REQUIRED);
        }
        if (actor.roleIn(groupSpace).isOwner()) {
            deleteSoleOwnerGroup(groupSpace);
            return;
        }
        deactivateMember(groupSpaceId, actor, GroupMemberStatus.LEFT, now);
    }

    @Transactional
    public void kick(Long accountId, Long groupSpaceId, Long targetMemberId) {
        GroupSpace groupSpace = commandService.lockGroupSpace(groupSpaceId);
        List<GroupMember> lockedMembers = commandService.lockMembers(groupSpaceId);
        GroupMember actor = requireActiveMembership(lockedMembers, accountId);
        Instant now = clock.instant();
        requireOwner(groupSpace, actor);
        GroupMember target = findActiveMember(lockedMembers, targetMemberId)
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_MEMBER_NOT_FOUND));
        if (target.roleIn(groupSpace).isOwner()) {
            throw new CalioException(ErrorCode.GROUP_OWNER_CANNOT_BE_REMOVED);
        }
        deactivateMember(groupSpaceId, target, GroupMemberStatus.REMOVED, now);
    }

    private byte[] credentialHash(AcceptGroupInvitationRequest request) {
        return credentialService.hashValidated(
                invitationCredentialType(request.credentialType()),
                request.credential()
        );
    }

    private InvitationCredentialType invitationCredentialType(
            GroupInvitationAcceptCredentialType credentialType
    ) {
        return credentialType == GroupInvitationAcceptCredentialType.LINK_TOKEN
                ? InvitationCredentialType.LINK_TOKEN
                : InvitationCredentialType.CODE;
    }

    private GroupInvitation locateInvitation(
            GroupInvitationAcceptCredentialType credentialType,
            byte[] credentialHash
    ) {
        return invitationQueryService.getInvitationByCredentialHash(
                invitationCredentialType(credentialType),
                credentialHash
        );
    }

    private void validateInvitation(
            GroupInvitation invitation,
            List<GroupMember> lockedMembers,
            Instant now
    ) {
        if (invitation.isExpiredAt(now)) {
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
            String nickname,
            Instant now
    ) {
        return commandService.create(groupSpace, accountId, nickname, now);
    }

    private void requireFreshInvitation(GroupInvitation invitation, GroupMember membership) {
        if (!normalize(invitation.getCreatedAt()).isAfter(membership.getStatusChangedAt())) {
            throw new CalioException(ErrorCode.GROUP_MEMBER_REJOIN_INVITATION_REQUIRED);
        }
    }

    private void requireAvailableNickname(Long groupSpaceId, String nickname, Long excludedMemberId) {
        if (queryService.hasActiveNicknameConflict(groupSpaceId, nickname, excludedMemberId)) {
            throw new CalioException(ErrorCode.GROUP_MEMBER_NICKNAME_CONFLICT);
        }
    }

    private void deactivateMember(
            Long groupSpaceId,
            GroupMember member,
            GroupMemberStatus inactiveStatus,
            Instant now
    ) {
        groupScheduleShareCleanupPort.cleanupMemberShares(groupSpaceId, member.getId());
        deleteIssuerInvitations(member.getId());
        commandService.changeStatus(member, inactiveStatus, now);
    }

    private void deleteSoleOwnerGroup(GroupSpace groupSpace) {
        groupScheduleShareCleanupPort.cleanupGroupShares(groupSpace.getId());
        invitationCommandService.deleteAllByGroupSpaceId(groupSpace.getId());
        groupSpaceCommandService.delete(groupSpace);
    }

    private void deleteIssuerInvitations(Long memberId) {
        invitationCommandService.deleteAllByCreatedByMemberId(memberId);
    }

    private GroupMember requireActiveMembership(Long groupSpaceId, Long accountId) {
        return queryService.getActiveMembership(groupSpaceId, accountId);
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
