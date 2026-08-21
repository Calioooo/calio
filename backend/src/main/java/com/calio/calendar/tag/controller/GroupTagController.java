package com.calio.calendar.tag.controller;

import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.tag.controller.dto.CustomTagRequest;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.service.GroupTagService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-spaces/{groupSpaceId}/tags")
public class GroupTagController {
    private final GroupTagService groupTagService;
    public GroupTagController(GroupTagService groupTagService) { this.groupTagService = groupTagService; }
    @GetMapping public List<TagResponse> list(@AuthenticationPrincipal AuthenticatedAccount account, @PathVariable Long groupSpaceId) { return groupTagService.list(account.accountId(), groupSpaceId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public TagResponse create(@AuthenticationPrincipal AuthenticatedAccount account, @PathVariable Long groupSpaceId, @Valid @RequestBody CustomTagRequest request) { return groupTagService.create(account.accountId(), groupSpaceId, request); }
    @PatchMapping("/{tagId}") public TagResponse update(@AuthenticationPrincipal AuthenticatedAccount account, @PathVariable Long groupSpaceId, @PathVariable Long tagId, @Valid @RequestBody CustomTagRequest request) { return groupTagService.update(account.accountId(), groupSpaceId, tagId, request); }
    @DeleteMapping("/{tagId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal AuthenticatedAccount account, @PathVariable Long groupSpaceId, @PathVariable Long tagId) { groupTagService.delete(account.accountId(), groupSpaceId, tagId); }
}
