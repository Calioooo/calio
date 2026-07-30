package com.calio.calendar.groupinvitation.controller;

import com.calio.calendar.groupinvitation.controller.dto.GroupInvitationListResponse;
import com.calio.calendar.groupinvitation.controller.dto.IssueGroupInvitationResponse;
import com.calio.calendar.groupinvitation.service.GroupInvitationService;
import com.calio.calendar.security.AuthenticatedAccount;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/group-spaces/{groupSpaceId}/invitations")
public class GroupInvitationController {

    private final GroupInvitationService groupInvitationService;

    public GroupInvitationController(GroupInvitationService groupInvitationService) {
        this.groupInvitationService = groupInvitationService;
    }

    @PostMapping
    public ResponseEntity<IssueGroupInvitationResponse> issue(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        IssueGroupInvitationResponse response =
                groupInvitationService.issue(account.accountId(), groupSpaceId);
        URI location = URI.create(
                "/api/group-spaces/%d/invitations/%d"
                        .formatted(groupSpaceId, response.invitationId())
        );
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public GroupInvitationListResponse list(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        return groupInvitationService.list(account.accountId(), groupSpaceId);
    }

    @DeleteMapping("/{invitationId}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId,
            @PathVariable("invitationId") Long invitationId
    ) {
        groupInvitationService.revoke(account.accountId(), groupSpaceId, invitationId);
        return ResponseEntity.noContent().build();
    }
}
