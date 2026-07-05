package com.calio.calendar.controller;

import com.calio.calendar.controller.dto.CustomTagRequest;
import com.calio.calendar.controller.dto.TagResponse;
import com.calio.calendar.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/custom-tags")
public class CustomTagController {

    private final TagService tagService;

    public CustomTagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse createCustomTag(@Valid @RequestBody CustomTagRequest request) {
        return tagService.createCustomTag(request);
    }

    @PutMapping("/{tagId}")
    public TagResponse updateCustomTag(
            @PathVariable Long tagId,
            @Valid @RequestBody CustomTagRequest request
    ) {
        return tagService.updateCustomTag(tagId, request);
    }

    @DeleteMapping("/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomTag(@PathVariable Long tagId) {
        tagService.deleteCustomTag(tagId);
    }
}
