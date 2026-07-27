package com.calio.calendar.groupspace.controller;

import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationRequest;
import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationResponse;
import com.calio.calendar.groupspace.controller.dto.GroupInvitationPreviewResponse;
import com.calio.calendar.groupspace.controller.dto.InvitationCredentialRequest;
import com.calio.calendar.groupspace.domain.GroupJoinResult;
import com.calio.calendar.groupspace.service.GroupSpaceService;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-invitations")
public class GroupInvitationController {

    private final GroupSpaceService groupSpaceService;

    public GroupInvitationController(GroupSpaceService groupSpaceService) {
        this.groupSpaceService = groupSpaceService;
    }

    @PostMapping("/preview")
    public GroupInvitationPreviewResponse previewInvitation(
            @Valid @RequestBody InvitationCredentialRequest request
    ) {
        return groupSpaceService.previewInvitation(request);
    }

    @PostMapping("/accept")
    public ResponseEntity<AcceptGroupInvitationResponse> acceptInvitation(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody AcceptGroupInvitationRequest request
    ) {
        AcceptGroupInvitationResponse response =
                groupSpaceService.acceptInvitation(account.accountId(), request);
        if (response.joinResult() != GroupJoinResult.JOINED) {
            return ResponseEntity.ok(response);
        }
        URI location = URI.create("/api/group-spaces/" + response.groupSpace().id());
        return ResponseEntity.created(location).body(response);
    }
}
