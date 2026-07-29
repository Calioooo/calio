package com.calio.calendar.groupspace.controller;

import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceDetailResponse;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceListResponse;
import com.calio.calendar.groupspace.controller.dto.UpdateGroupSpaceRequest;
import com.calio.calendar.groupspace.service.GroupSpaceService;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/group-spaces")
public class GroupSpaceController {

    private final GroupSpaceService groupSpaceService;

    public GroupSpaceController(GroupSpaceService groupSpaceService) {
        this.groupSpaceService = groupSpaceService;
    }

    @PostMapping
    public ResponseEntity<GroupSpaceDetailResponse> create(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CreateGroupSpaceRequest request
    ) {
        GroupSpaceDetailResponse response = groupSpaceService.create(account.accountId(), request);
        URI location = URI.create("/api/group-spaces/" + response.groupSpaceId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public GroupSpaceListResponse list(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return groupSpaceService.list(account.accountId());
    }

    @GetMapping("/{groupSpaceId}")
    public GroupSpaceDetailResponse get(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        return groupSpaceService.get(account.accountId(), groupSpaceId);
    }

    @PatchMapping("/{groupSpaceId}")
    public GroupSpaceDetailResponse update(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId,
            @Valid @RequestBody UpdateGroupSpaceRequest request
    ) {
        return groupSpaceService.update(account.accountId(), groupSpaceId, request);
    }

    @DeleteMapping("/{groupSpaceId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        groupSpaceService.delete(account.accountId(), groupSpaceId);
        return ResponseEntity.noContent().build();
    }
}
