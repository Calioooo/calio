package com.calio.calendar.groupspace.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.config.GroupInvitationProperties;
import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationRequest;
import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationResponse;
import com.calio.calendar.groupspace.controller.dto.CreateGroupInvitationResponse;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.controller.dto.GroupInvitationListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupInvitationPreviewResponse;
import com.calio.calendar.groupspace.controller.dto.GroupInvitationSummaryResponse;
import com.calio.calendar.groupspace.controller.dto.GroupMemberListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupMemberResponse;
import com.calio.calendar.groupspace.controller.dto.GroupOwnerDelegateDto;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceResponseDto;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceSummaryResponse;
import com.calio.calendar.groupspace.controller.dto.InvitationCredentialRequest;
import com.calio.calendar.groupspace.controller.dto.UpdateGroupSpaceRequest;
import com.calio.calendar.groupspace.domain.GroupInvitation;
import com.calio.calendar.groupspace.domain.GroupJoinResult;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.domain.InvitationCredentialType;
import com.calio.calendar.groupspace.repository.GroupInvitationRepository;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.groupspace.repository.InvitationLocator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GroupSpaceService {

    private static final Duration INVITATION_LIFETIME = Duration.ofHours(24);
    private static final int MAX_CREDENTIAL_GENERATION_ATTEMPTS = 3;

    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final AccountRepository accountRepository;
    private final GroupSpaceInputNormalizer inputNormalizer;
    private final InvitationCredentialCodec credentialCodec;
    private final GroupInvitationProperties invitationProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public GroupSpaceService(
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository,
            GroupInvitationRepository groupInvitationRepository,
            AccountRepository accountRepository,
            GroupSpaceInputNormalizer inputNormalizer,
            InvitationCredentialCodec credentialCodec,
            GroupInvitationProperties invitationProperties,
            Clock clock,
            TransactionTemplate transactionTemplate
    ) {
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInvitationRepository = groupInvitationRepository;
        this.accountRepository = accountRepository;
        this.inputNormalizer = inputNormalizer;
        this.credentialCodec = credentialCodec;
        this.invitationProperties = invitationProperties;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public GroupSpaceResponseDto createGroup(Long accountId, CreateGroupSpaceRequest request) {
        String name = inputNormalizer.normalizeName(request.name());
        String emoji = inputNormalizer.normalizeEmoji(request.emoji());
        String nickname = inputNormalizer.normalizeNickname(request.nickname());
        Account account = accountRepository.getReferenceById(accountId);
        GroupSpace groupSpace = groupSpaceRepository.save(new GroupSpace(name, emoji, accountId));
        GroupMember owner = groupMemberRepository.save(new GroupMember(groupSpace, account, nickname));
        groupMemberRepository.flush();
        return GroupSpaceResponseDto.from(groupSpace, owner, 1);
    }

    @Transactional(readOnly = true)
    public GroupSpaceListResponse listGroups(Long accountId) {
        List<GroupSpaceSummaryResponse> groups = groupMemberRepository
                .findVisibleGroups(accountId, GroupMemberStatus.ACTIVE)
                .stream()
                .map(membership -> GroupSpaceSummaryResponse.from(
                        membership.getGroupSpace(),
                        membership,
                        activeMemberCount(membership.getGroupSpace().getId())
                ))
                .toList();
        return new GroupSpaceListResponse(groups);
    }

    @Transactional(readOnly = true)
    public GroupSpaceResponseDto getGroup(Long accountId, Long groupSpaceId) {
        GroupMember membership = findActiveMembership(groupSpaceId, accountId);
        return GroupSpaceResponseDto.from(
                membership.getGroupSpace(),
                membership,
                activeMemberCount(groupSpaceId)
        );
    }

    @Transactional
    public GroupSpaceResponseDto updateGroup(
            Long accountId,
            Long groupSpaceId,
            UpdateGroupSpaceRequest request
    ) {
        validatePatchPresence(request);
        GroupSpace groupSpace = lockGroup(groupSpaceId);
        GroupMember actor = lockActiveMembership(groupSpace, accountId);
        requireOwner(groupSpace, actor);
        String name = request.isNamePresent()
                ? inputNormalizer.normalizeName(request.getName())
                : groupSpace.getName();
        String emoji = request.isEmojiPresent()
                ? inputNormalizer.normalizeEmoji(request.getEmoji())
                : groupSpace.getEmoji();
        groupSpace.update(name, emoji);
        groupSpaceRepository.flush();
        return GroupSpaceResponseDto.from(groupSpace, actor, activeMemberCount(groupSpaceId));
    }

    @Transactional
    public void deleteGroup(Long accountId, Long groupSpaceId) {
        GroupSpace groupSpace = lockGroup(groupSpaceId);
        List<GroupMember> members = lockAllMembers(groupSpaceId);
        GroupMember actor = activeMember(members, accountId)
                .orElseThrow(this::groupNotFound);
        requireOwner(groupSpace, actor);
        groupInvitationRepository.findAllForUpdate(groupSpaceId);
        hardDeleteGroup(groupSpace);
    }

    public CreateGroupInvitationResponse createInvitation(Long accountId, Long groupSpaceId) {
        for (int attempt = 0; attempt < MAX_CREDENTIAL_GENERATION_ATTEMPTS; attempt++) {
            InvitationCredentialCodec.CredentialPair credentials = credentialCodec.generate();
            try {
                return transactionTemplate.execute(status ->
                        persistInvitation(accountId, groupSpaceId, credentials)
                );
            } catch (DataIntegrityViolationException ignored) {
                // A fresh transaction and credential pair are required for the next attempt.
            }
        }
        throw new CalioException(ErrorCode.GROUP_INVITATION_GENERATION_FAILED);
    }

    @Transactional(readOnly = true)
    public GroupInvitationListResponse listInvitations(Long accountId, Long groupSpaceId) {
        GroupMember actor = findActiveMembership(groupSpaceId, accountId);
        GroupSpace groupSpace = actor.getGroupSpace();
        List<GroupInvitation> invitations = groupSpace.isOwner(accountId)
                ? groupInvitationRepository.findByGroupSpace_IdOrderByIdDesc(groupSpaceId)
                : groupInvitationRepository.findByGroupSpace_IdAndIssuer_IdOrderByIdDesc(
                        groupSpaceId,
                        actor.getId()
                );
        Instant now = clock.instant();
        return new GroupInvitationListResponse(invitations.stream()
                .map(invitation -> GroupInvitationSummaryResponse.from(invitation, now))
                .toList());
    }

    @Transactional
    public void revokeInvitation(Long accountId, Long groupSpaceId, Long invitationId) {
        GroupSpace groupSpace = lockGroup(groupSpaceId);
        GroupMember actor = lockActiveMembership(groupSpace, accountId);
        GroupInvitation invitation = groupInvitationRepository
                .findByIdForUpdate(groupSpaceId, invitationId)
                .orElseThrow(this::invitationNotFound);
        boolean canRevoke = groupSpace.isOwner(accountId)
                || invitation.getIssuer().getId().equals(actor.getId());
        if (!canRevoke) {
            throw invitationNotFound();
        }
        groupInvitationRepository.delete(invitation);
    }

    @Transactional(readOnly = true)
    public GroupInvitationPreviewResponse previewInvitation(InvitationCredentialRequest request) {
        GroupInvitation invitation = findInvitation(request.credentialType(), request.credential());
        validateInvitation(invitation, request.credentialType(), request.credential());
        return GroupInvitationPreviewResponse.from(
                invitation,
                activeMemberCount(invitation.getGroupSpace().getId())
        );
    }

    public AcceptGroupInvitationResponse acceptInvitation(
            Long accountId,
            AcceptGroupInvitationRequest request
    ) {
        String nickname = inputNormalizer.normalizeNickname(request.nickname());
        InvitationLocator locator = locateInvitation(request.credentialType(), request.credential());
        try {
            return transactionTemplate.execute(status ->
                    acceptInTransaction(accountId, request, nickname, locator)
            );
        } catch (DataIntegrityViolationException exception) {
            return resolveConcurrentAccept(accountId, locator.groupSpaceId());
        }
    }

    @Transactional(readOnly = true)
    public GroupMemberListResponse listMembers(Long accountId, Long groupSpaceId) {
        GroupSpace groupSpace = findActiveMembership(groupSpaceId, accountId).getGroupSpace();
        Comparator<GroupMember> ordering = Comparator
                .comparing((GroupMember member) -> !groupSpace.isOwner(member.getAccountId()))
                .thenComparing(GroupMember::getUpdatedAt)
                .thenComparing(GroupMember::getId);
        List<GroupMemberResponse> members = groupMemberRepository
                .findByGroupSpace_IdAndStatus(groupSpaceId, GroupMemberStatus.ACTIVE)
                .stream()
                .sorted(ordering)
                .map(member -> GroupMemberResponse.from(member, groupSpace.getOwnerAccountId()))
                .toList();
        return new GroupMemberListResponse(members);
    }

    @Transactional
    public GroupOwnerDelegateDto transferOwner(Long accountId, Long groupSpaceId, Long targetMemberId) {
        GroupSpace groupSpace = lockGroup(groupSpaceId);
        GroupMember actorCandidate = groupMemberRepository
                .findByGroupSpace_IdAndAccount_Id(groupSpaceId, accountId)
                .orElseThrow(this::groupNotFound);
        if (!actorCandidate.isActive()) {
            throw groupNotFound();
        }
        Optional<GroupMember> targetOptional =
                groupMemberRepository.findByIdAndGroupSpace_Id(targetMemberId, groupSpaceId);
        if (targetOptional.isEmpty()) {
            GroupMember actor = memberById(
                    lockMembers(groupSpaceId, List.of(actorCandidate.getId())),
                    actorCandidate.getId()
            );
            requireOwner(groupSpace, actor);
            throw invalidTransfer();
        }
        GroupMember targetCandidate = targetOptional.get();
        List<GroupMember> locked = lockMembers(groupSpaceId, List.of(actorCandidate.getId(), targetCandidate.getId()));
        GroupMember actor = memberById(locked, actorCandidate.getId());
        GroupMember target = memberById(locked, targetCandidate.getId());
        requireOwner(groupSpace, actor);
        if (!target.isActive() || target.getId().equals(actor.getId())) {
            throw invalidTransfer();
        }
        groupSpace.transferOwnership(target.getAccountId());
        groupSpaceRepository.flush();
        return new GroupOwnerDelegateDto(
                GroupMemberResponse.from(actor, groupSpace.getOwnerAccountId()),
                GroupMemberResponse.from(target, groupSpace.getOwnerAccountId())
        );
    }

    @Transactional
    public void removeMember(Long accountId, Long groupSpaceId, Long memberId) {
        GroupSpace groupSpace = lockGroup(groupSpaceId);
        GroupMember actorCandidate = groupMemberRepository
                .findByGroupSpace_IdAndAccount_Id(groupSpaceId, accountId)
                .orElseThrow(this::groupNotFound);
        if (!actorCandidate.isActive()) {
            throw groupNotFound();
        }
        Optional<GroupMember> targetOptional =
                groupMemberRepository.findByIdAndGroupSpace_Id(memberId, groupSpaceId);
        if (targetOptional.isEmpty()) {
            GroupMember actor = memberById(
                    lockMembers(groupSpaceId, List.of(actorCandidate.getId())),
                    actorCandidate.getId()
            );
            requireOwner(groupSpace, actor);
            throw memberNotFound();
        }
        GroupMember targetCandidate = targetOptional.get();
        List<GroupMember> locked = lockMembers(groupSpaceId, List.of(actorCandidate.getId(), targetCandidate.getId()));
        GroupMember actor = memberById(locked, actorCandidate.getId());
        GroupMember target = memberById(locked, targetCandidate.getId());
        requireOwner(groupSpace, actor);
        if (!target.isActive()) {
            throw memberNotFound();
        }
        if (groupSpace.isOwner(target.getAccountId())) {
            throw new CalioException(ErrorCode.GROUP_OWNER_CANNOT_BE_REMOVED);
        }
        groupInvitationRepository.findAllForUpdate(groupSpaceId);
        groupInvitationRepository.deleteByIssuer_Id(target.getId());
        target.remove();
    }

    @Transactional
    public void leaveGroup(Long accountId, Long groupSpaceId) {
        GroupSpace groupSpace = lockGroup(groupSpaceId);
        List<GroupMember> members = lockAllMembers(groupSpaceId);
        GroupMember actor = activeMember(members, accountId)
                .orElseThrow(this::groupNotFound);
        boolean isOwner = groupSpace.isOwner(accountId);
        if (isOwner && activeMemberCount(groupSpaceId) > 1) {
            throw new CalioException(ErrorCode.GROUP_OWNER_TRANSFER_REQUIRED);
        }
        groupInvitationRepository.findAllForUpdate(groupSpaceId);
        if (isOwner) {
            hardDeleteGroup(groupSpace);
            return;
        }
        groupInvitationRepository.deleteByIssuer_Id(actor.getId());
        actor.leave();
    }

    private CreateGroupInvitationResponse persistInvitation(
            Long accountId,
            Long groupSpaceId,
            InvitationCredentialCodec.CredentialPair credentials
    ) {
        GroupSpace groupSpace = lockGroup(groupSpaceId);
        GroupMember issuer = lockActiveMembership(groupSpace, accountId);
        groupInvitationRepository.findAllForUpdate(groupSpaceId);
        Instant expiresAt = clock.instant().plus(INVITATION_LIFETIME);
        GroupInvitation invitation = groupInvitationRepository.saveAndFlush(new GroupInvitation(
                groupSpace,
                issuer,
                credentials.linkTokenHash(),
                credentials.inviteCodeHash(),
                expiresAt
        ));
        return new CreateGroupInvitationResponse(
                invitation.getId(),
                invitationProperties.buildInviteUrl(credentials.linkToken()),
                credentials.inviteCode(),
                expiresAt
        );
    }

    private AcceptGroupInvitationResponse acceptInTransaction(
            Long accountId,
            AcceptGroupInvitationRequest request,
            String nickname,
            InvitationLocator locator
    ) {
        GroupSpace groupSpace = lockGroup(locator.groupSpaceId());
        GroupInvitation candidate = groupInvitationRepository
                .findByIdAndGroupSpace_Id(locator.invitationId(), groupSpace.getId())
                .orElseThrow(this::invitationNotFound);
        Optional<GroupMember> existing = groupMemberRepository
                .findByGroupSpace_IdAndAccount_Id(groupSpace.getId(), accountId);
        List<Long> memberIds = new ArrayList<>();
        memberIds.add(candidate.getIssuer().getId());
        existing.map(GroupMember::getId).ifPresent(memberIds::add);
        List<GroupMember> lockedMembers = lockMembers(groupSpace.getId(), memberIds);
        GroupInvitation invitation = groupInvitationRepository
                .findByIdForUpdate(groupSpace.getId(), locator.invitationId())
                .orElseThrow(this::invitationNotFound);
        validateInvitation(invitation, request.credentialType(), request.credential());
        GroupMember issuer = memberById(lockedMembers, invitation.getIssuer().getId());
        if (!issuer.isActive()) {
            throw invitationNotFound();
        }
        GroupMember membership = existing
                .map(member -> memberById(lockedMembers, member.getId()))
                .orElse(null);
        return applyAccept(groupSpace, membership, accountId, nickname, invitation);
    }

    private AcceptGroupInvitationResponse applyAccept(
            GroupSpace groupSpace,
            GroupMember membership,
            Long accountId,
            String nickname,
            GroupInvitation invitation
    ) {
        if (membership != null && membership.isActive()) {
            return acceptResponse(GroupJoinResult.ALREADY_MEMBER, groupSpace, membership);
        }
        if (membership != null) {
            return rejoin(groupSpace, membership, nickname, invitation);
        }
        Account account = accountRepository.getReferenceById(accountId);
        GroupMember joined = groupMemberRepository.saveAndFlush(new GroupMember(groupSpace, account, nickname));
        return acceptResponse(GroupJoinResult.JOINED, groupSpace, joined);
    }

    private AcceptGroupInvitationResponse rejoin(
            GroupSpace groupSpace,
            GroupMember membership,
            String nickname,
            GroupInvitation invitation
    ) {
        if (!invitation.getCreatedAt().isAfter(membership.getUpdatedAt())) {
            throw new CalioException(ErrorCode.GROUP_MEMBER_REJOIN_INVITATION_REQUIRED);
        }
        membership.rejoin(nickname);
        groupMemberRepository.flush();
        return acceptResponse(GroupJoinResult.REJOINED, groupSpace, membership);
    }

    private AcceptGroupInvitationResponse acceptResponse(
            GroupJoinResult result,
            GroupSpace groupSpace,
            GroupMember membership
    ) {
        return AcceptGroupInvitationResponse.from(
                result,
                groupSpace,
                membership,
                activeMemberCount(groupSpace.getId())
        );
    }

    private AcceptGroupInvitationResponse resolveConcurrentAccept(Long accountId, Long groupSpaceId) {
        return transactionTemplate.execute(status -> groupMemberRepository
                .findByGroupSpace_IdAndAccount_IdAndStatus(
                        groupSpaceId,
                        accountId,
                        GroupMemberStatus.ACTIVE
                )
                .map(member -> acceptResponse(GroupJoinResult.ALREADY_MEMBER, member.getGroupSpace(), member))
                .orElseThrow(() -> new CalioException(ErrorCode.GROUP_MEMBER_NICKNAME_CONFLICT))
        );
    }

    private InvitationLocator locateInvitation(InvitationCredentialType type, String rawCredential) {
        byte[] digest = credentialCodec.digest(type, rawCredential);
        Optional<InvitationLocator> locator = type == InvitationCredentialType.LINK_TOKEN
                ? groupInvitationRepository.locateByLinkTokenHash(digest)
                : groupInvitationRepository.locateByInviteCodeHash(digest);
        return locator.orElseThrow(this::invitationNotFound);
    }

    private GroupInvitation findInvitation(InvitationCredentialType type, String rawCredential) {
        byte[] digest = credentialCodec.digest(type, rawCredential);
        Optional<GroupInvitation> invitation = type == InvitationCredentialType.LINK_TOKEN
                ? groupInvitationRepository.findByLinkTokenHash(digest)
                : groupInvitationRepository.findByInviteCodeHash(digest);
        return invitation.orElseThrow(this::invitationNotFound);
    }

    private void validateInvitation(
            GroupInvitation invitation,
            InvitationCredentialType type,
            String rawCredential
    ) {
        byte[] digest = credentialCodec.digest(type, rawCredential);
        if (!invitation.matches(type, digest) || !invitation.getIssuer().isActive()) {
            throw invitationNotFound();
        }
        if (invitation.isExpired(clock.instant())) {
            throw new CalioException(ErrorCode.GROUP_INVITATION_EXPIRED);
        }
    }

    private GroupMember findActiveMembership(Long groupSpaceId, Long accountId) {
        return groupMemberRepository
                .findByGroupSpace_IdAndAccount_IdAndStatus(
                        groupSpaceId,
                        accountId,
                        GroupMemberStatus.ACTIVE
                )
                .orElseThrow(this::groupNotFound);
    }

    private GroupMember lockActiveMembership(GroupSpace groupSpace, Long accountId) {
        GroupMember member = groupMemberRepository
                .findByAccountForUpdate(groupSpace.getId(), accountId)
                .orElseThrow(this::groupNotFound);
        if (!member.isActive()) {
            throw groupNotFound();
        }
        return member;
    }

    private GroupSpace lockGroup(Long groupSpaceId) {
        return groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(this::groupNotFound);
    }

    private List<GroupMember> lockAllMembers(Long groupSpaceId) {
        List<Long> memberIds = groupMemberRepository
                .findByGroupSpace_IdOrderByIdAsc(groupSpaceId)
                .stream()
                .map(GroupMember::getId)
                .toList();
        return lockMembers(groupSpaceId, memberIds);
    }

    private List<GroupMember> lockMembers(Long groupSpaceId, List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return groupMemberRepository.findAllByIdForUpdate(
                groupSpaceId,
                memberIds.stream().distinct().sorted().toList()
        );
    }

    private Optional<GroupMember> activeMember(List<GroupMember> members, Long accountId) {
        return members.stream()
                .filter(GroupMember::isActive)
                .filter(member -> member.getAccountId().equals(accountId))
                .findFirst();
    }

    private GroupMember memberById(List<GroupMember> members, Long memberId) {
        return members.stream()
                .filter(member -> member.getId().equals(memberId))
                .findFirst()
                .orElseThrow(this::memberNotFound);
    }

    private int activeMemberCount(Long groupSpaceId) {
        return Math.toIntExact(groupMemberRepository.countByGroupSpace_IdAndStatus(
                groupSpaceId,
                GroupMemberStatus.ACTIVE
        ));
    }

    private void requireOwner(GroupSpace groupSpace, GroupMember actor) {
        if (!actor.isActive()) {
            throw groupNotFound();
        }
        if (!groupSpace.isOwner(actor.getAccountId())) {
            throw new CalioException(ErrorCode.GROUP_OWNER_REQUIRED);
        }
    }

    private void validatePatchPresence(UpdateGroupSpaceRequest request) {
        if (request == null || (!request.isNamePresent() && !request.isEmojiPresent())) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private void hardDeleteGroup(GroupSpace groupSpace) {
        groupInvitationRepository.deleteByGroupSpace_Id(groupSpace.getId());
        groupMemberRepository.deleteByGroupSpace_Id(groupSpace.getId());
        groupSpaceRepository.delete(groupSpace);
    }

    private CalioException groupNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }

    private CalioException memberNotFound() {
        return new CalioException(ErrorCode.GROUP_MEMBER_NOT_FOUND);
    }

    private CalioException invitationNotFound() {
        return new CalioException(ErrorCode.GROUP_INVITATION_NOT_FOUND);
    }

    private CalioException invalidTransfer() {
        return new CalioException(ErrorCode.GROUP_OWNER_TRANSFER_INVALID);
    }
}
