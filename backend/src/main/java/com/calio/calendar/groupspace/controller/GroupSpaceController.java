package com.calio.calendar.groupspace.controller;

import com.calio.calendar.groupspace.controller.dto.CreateGroupInvitationResponse;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.controller.dto.GroupInvitationListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupMemberListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupOwnerDelegateDto;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceResponseDto;
import com.calio.calendar.groupspace.controller.dto.TransferGroupOwnerRequest;
import com.calio.calendar.groupspace.controller.dto.UpdateGroupSpaceRequest;
import com.calio.calendar.groupspace.service.GroupSpaceService;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-spaces")
public class GroupSpaceController {

    private final GroupSpaceService groupSpaceService;

    public GroupSpaceController(GroupSpaceService groupSpaceService) {
        this.groupSpaceService = groupSpaceService;
    }

    @PostMapping
    public ResponseEntity<GroupSpaceResponseDto> createGroup(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CreateGroupSpaceRequest request
    ) {
        GroupSpaceResponseDto response = groupSpaceService.createGroup(account.accountId(), request);
        return ResponseEntity.created(groupLocation(response.id())).body(response);
    }

    @GetMapping
    public GroupSpaceListResponse listGroups(@AuthenticationPrincipal AuthenticatedAccount account) {
        return groupSpaceService.listGroups(account.accountId());
    }

    @GetMapping("/{groupSpaceId}")
    public GroupSpaceResponseDto getGroup(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        return groupSpaceService.getGroup(account.accountId(), groupSpaceId);
    }

    @PatchMapping("/{groupSpaceId}")
    public GroupSpaceResponseDto updateGroup(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId,
            @RequestBody UpdateGroupSpaceRequest request
    ) {
        return groupSpaceService.updateGroup(account.accountId(), groupSpaceId, request);
    }

    @DeleteMapping("/{groupSpaceId}")
    public ResponseEntity<Void> deleteGroup(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        groupSpaceService.deleteGroup(account.accountId(), groupSpaceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupSpaceId}/invitations")
    public ResponseEntity<CreateGroupInvitationResponse> createInvitation(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        CreateGroupInvitationResponse response =
                groupSpaceService.createInvitation(account.accountId(), groupSpaceId);
        URI location = URI.create("/api/group-spaces/" + groupSpaceId
                + "/invitations/" + response.invitationId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{groupSpaceId}/invitations")
    public GroupInvitationListResponse listInvitations(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        return groupSpaceService.listInvitations(account.accountId(), groupSpaceId);
    }

    @DeleteMapping("/{groupSpaceId}/invitations/{invitationId}")
    public ResponseEntity<Void> revokeInvitation(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId,
            @PathVariable("invitationId") Long invitationId
    ) {
        groupSpaceService.revokeInvitation(account.accountId(), groupSpaceId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupSpaceId}/members")
    public GroupMemberListResponse listMembers(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        return groupSpaceService.listMembers(account.accountId(), groupSpaceId);
    }

    @PostMapping("/{groupSpaceId}/owner-transfer")
    public GroupOwnerDelegateDto transferOwner(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId,
            @Valid @RequestBody TransferGroupOwnerRequest request
    ) {
        return groupSpaceService.transferOwner(
                account.accountId(),
                groupSpaceId,
                request.targetMemberId()
        );
    }

    @DeleteMapping("/{groupSpaceId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId,
            @PathVariable("memberId") Long memberId
    ) {
        groupSpaceService.removeMember(account.accountId(), groupSpaceId, memberId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupSpaceId}/members/me")
    public ResponseEntity<Void> leaveGroup(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        groupSpaceService.leaveGroup(account.accountId(), groupSpaceId);
        return ResponseEntity.noContent().build();
    }

    private URI groupLocation(Long groupSpaceId) {
        return URI.create("/api/group-spaces/" + groupSpaceId);
    }
}
