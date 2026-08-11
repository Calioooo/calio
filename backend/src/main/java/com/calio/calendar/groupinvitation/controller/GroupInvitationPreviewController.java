package com.calio.calendar.groupinvitation.controller;

import com.calio.calendar.groupinvitation.controller.dto.PreviewGroupInvitationRequest;
import com.calio.calendar.groupinvitation.controller.dto.PreviewGroupInvitationResponse;
import com.calio.calendar.groupinvitation.service.GroupInvitationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-invitations")
public class GroupInvitationPreviewController {

    private final GroupInvitationService groupInvitationService;

    public GroupInvitationPreviewController(GroupInvitationService groupInvitationService) {
        this.groupInvitationService = groupInvitationService;
    }

    @PostMapping("/preview")
    public ResponseEntity<PreviewGroupInvitationResponse> preview(
            @Valid @RequestBody PreviewGroupInvitationRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(groupInvitationService.preview(request));
    }
}
