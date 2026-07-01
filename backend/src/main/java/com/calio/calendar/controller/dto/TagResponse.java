package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.Tag;

public record TagResponse(
        Long id,
        String title,
        String colorCode
) {

    public static TagResponse from(Tag tag) {
        if (tag == null) {
            return null;
        }

        return new TagResponse(
                tag.getId(),
                tag.getTitle(),
                tag.getColorCode()
        );
    }
}
