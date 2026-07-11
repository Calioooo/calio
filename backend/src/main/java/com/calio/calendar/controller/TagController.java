package com.calio.calendar.controller;

import com.calio.calendar.controller.dto.CustomTagRequest;
import com.calio.calendar.controller.dto.TagResponse;
import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.service.TagService;
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

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/api/tags")
    public List<TagResponse> listTags(@AuthenticationPrincipal AuthenticatedAccount account) {
        return tagService.listTags(account.accountId());
    }

    @PostMapping("/api/custom-tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse createCustomTag(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CustomTagRequest request
    ) {
        return tagService.createCustomTag(account.accountId(), request);
    }

    @PutMapping("/api/custom-tags/{tagId}")
    public TagResponse updateCustomTag(
            @PathVariable("tagId") Long tagId,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CustomTagRequest request
    ) {
        return tagService.updateCustomTag(account.accountId(), tagId, request);
    }

    @DeleteMapping("/api/custom-tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomTag(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("tagId") Long tagId
    ) {
        tagService.deleteCustomTag(account.accountId(), tagId);
    }
}
