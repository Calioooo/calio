package com.calio.calendar.groupinvitation.controller;

import com.calio.calendar.groupinvitation.controller.dto.PreviewGroupInvitationRequest;
import com.calio.calendar.groupinvitation.controller.dto.PreviewGroupInvitationResponse;
import com.calio.calendar.groupinvitation.service.GroupInvitationPreviewService;
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

    private final GroupInvitationPreviewService previewService;

    public GroupInvitationPreviewController(GroupInvitationPreviewService previewService) {
        this.previewService = previewService;
    }

    @PostMapping("/preview")
    public ResponseEntity<PreviewGroupInvitationResponse> preview(
            @Valid @RequestBody PreviewGroupInvitationRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(previewService.preview(request));
    }
}
