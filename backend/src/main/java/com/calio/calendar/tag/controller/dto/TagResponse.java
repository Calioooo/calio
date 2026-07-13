package com.calio.calendar.tag.controller.dto;

import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;

public record TagResponse(
        Long id,
        String title,
        String colorCode,
        TagType tagType
) {

    public static TagResponse from(Tag tag) {
        if (tag == null) {
            return null;
        }

        return new TagResponse(
                tag.getId(),
                tag.getTitle(),
                tag.getColorCode(),
                tag.getTagType()
        );
    }
}
