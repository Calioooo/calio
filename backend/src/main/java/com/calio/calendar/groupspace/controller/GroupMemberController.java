package com.calio.calendar.groupspace.controller;

import com.calio.calendar.groupspace.controller.dto.GroupMemberListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupMembershipResponse;
import com.calio.calendar.groupspace.controller.dto.TransferGroupOwnerRequest;
import com.calio.calendar.groupspace.controller.dto.TransferGroupOwnerResponse;
import com.calio.calendar.groupspace.controller.dto.UpdateAnonymousSharingRequest;
import com.calio.calendar.groupspace.service.GroupMembershipService;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/group-spaces/{groupSpaceId}")
public class GroupMemberController {

    private final GroupMembershipService groupMembershipService;

    public GroupMemberController(GroupMembershipService groupMembershipService) {
        this.groupMembershipService = groupMembershipService;
    }

    @GetMapping("/members")
    public GroupMemberListResponse listMembers(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        return groupMembershipService.listActiveMembers(account.accountId(), groupSpaceId);
    }

    @PostMapping("/owner-transfer")
    public TransferGroupOwnerResponse transferOwnership(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId,
            @Valid @RequestBody TransferGroupOwnerRequest request
    ) {
        return groupMembershipService.transferOwnership(
                account.accountId(),
                groupSpaceId,
                request.targetMemberId()
        );
    }

    @PatchMapping("/members/me/anonymous-sharing")
    public GroupMembershipResponse changeAnonymousSharing(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId,
            @Valid @RequestBody UpdateAnonymousSharingRequest request
    ) {
        return groupMembershipService.changeAnonymousSharing(
                account.accountId(),
                groupSpaceId,
                request.isAnonymous()
        );
    }

    @DeleteMapping("/members/me")
    public ResponseEntity<Void> leave(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        groupMembershipService.leave(account.accountId(), groupSpaceId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<Void> kick(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId,
            @PathVariable("memberId") Long memberId
    ) {
        groupMembershipService.kick(account.accountId(), groupSpaceId, memberId);
        return ResponseEntity.noContent().build();
    }
}
