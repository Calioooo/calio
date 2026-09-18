package com.calio.calendar.tag.controller;

import com.calio.calendar.tag.controller.dto.CustomTagRequest;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.tag.usecase.CreatePersonalCustomTagUseCase;
import com.calio.calendar.tag.usecase.DeletePersonalCustomTagUseCase;
import com.calio.calendar.tag.usecase.ListPersonalTagsUseCase;
import com.calio.calendar.tag.usecase.UpdatePersonalCustomTagUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TagController {

    private final ListPersonalTagsUseCase listPersonalTagsUseCase;
    private final CreatePersonalCustomTagUseCase createPersonalCustomTagUseCase;
    private final UpdatePersonalCustomTagUseCase updatePersonalCustomTagUseCase;
    private final DeletePersonalCustomTagUseCase deletePersonalCustomTagUseCase;

    public TagController(
            ListPersonalTagsUseCase listPersonalTagsUseCase,
            CreatePersonalCustomTagUseCase createPersonalCustomTagUseCase,
            UpdatePersonalCustomTagUseCase updatePersonalCustomTagUseCase,
            DeletePersonalCustomTagUseCase deletePersonalCustomTagUseCase
    ) {
        this.listPersonalTagsUseCase = listPersonalTagsUseCase;
        this.createPersonalCustomTagUseCase = createPersonalCustomTagUseCase;
        this.updatePersonalCustomTagUseCase = updatePersonalCustomTagUseCase;
        this.deletePersonalCustomTagUseCase = deletePersonalCustomTagUseCase;
    }

    @GetMapping("/api/tags")
    public List<TagResponse> listTags(@AuthenticationPrincipal AuthenticatedAccount account) {
        return listPersonalTagsUseCase.list(account.accountId());
    }

    @PostMapping("/api/custom-tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse createCustomTag(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CustomTagRequest request
    ) {
        return createPersonalCustomTagUseCase.create(account.accountId(), request.title(), request.colorCode());
    }

    @PutMapping("/api/custom-tags/{tagId}")
    public TagResponse updateCustomTag(
            @PathVariable("tagId") Long tagId,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CustomTagRequest request
    ) {
        return updatePersonalCustomTagUseCase.update(account.accountId(), tagId, request.title(), request.colorCode());
    }

    @DeleteMapping("/api/custom-tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomTag(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("tagId") Long tagId
    ) {
        deletePersonalCustomTagUseCase.delete(account.accountId(), tagId);
    }
}
