package com.calio.calendar.tag.controller;

import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.tag.controller.dto.CustomTagRequest;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.usecase.CreateGroupCustomTagUseCase;
import com.calio.calendar.tag.usecase.DeleteGroupCustomTagUseCase;
import com.calio.calendar.tag.usecase.ListGroupTagsUseCase;
import com.calio.calendar.tag.usecase.UpdateGroupCustomTagUseCase;
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

    private final ListGroupTagsUseCase listGroupTagsUseCase;
    private final CreateGroupCustomTagUseCase createGroupCustomTagUseCase;
    private final UpdateGroupCustomTagUseCase updateGroupCustomTagUseCase;
    private final DeleteGroupCustomTagUseCase deleteGroupCustomTagUseCase;

    public GroupTagController(
            ListGroupTagsUseCase listGroupTagsUseCase,
            CreateGroupCustomTagUseCase createGroupCustomTagUseCase,
            UpdateGroupCustomTagUseCase updateGroupCustomTagUseCase,
            DeleteGroupCustomTagUseCase deleteGroupCustomTagUseCase
    ) {
        this.listGroupTagsUseCase = listGroupTagsUseCase;
        this.createGroupCustomTagUseCase = createGroupCustomTagUseCase;
        this.updateGroupCustomTagUseCase = updateGroupCustomTagUseCase;
        this.deleteGroupCustomTagUseCase = deleteGroupCustomTagUseCase;
    }

    @GetMapping
    public List<TagResponse> list(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId
    ) {
        return listGroupTagsUseCase.list(account.accountId(), groupSpaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @Valid @RequestBody CustomTagRequest request
    ) {
        return createGroupCustomTagUseCase.create(account.accountId(), groupSpaceId, request.title(), request.colorCode());
    }

    @PatchMapping("/{tagId}")
    public TagResponse update(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long tagId,
            @Valid @RequestBody CustomTagRequest request
    ) {
        return updateGroupCustomTagUseCase.update(account.accountId(), groupSpaceId, tagId, request.title(), request.colorCode());
    }

    @DeleteMapping("/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long tagId
    ) {
        deleteGroupCustomTagUseCase.delete(account.accountId(), groupSpaceId, tagId);
    }
}
