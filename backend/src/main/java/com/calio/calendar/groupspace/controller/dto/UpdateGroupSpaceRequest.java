package com.calio.calendar.groupspace.controller.dto;

import com.fasterxml.jackson.annotation.JsonSetter;

public final class UpdateGroupSpaceRequest {

    private boolean namePresent;
    private String name;
    private boolean emojiPresent;
    private String emoji;

    public UpdateGroupSpaceRequest() {
    }

    @JsonSetter("name")
    public void setName(String name) {
        this.namePresent = true;
        this.name = name;
    }

    @JsonSetter("emoji")
    public void setEmoji(String emoji) {
        this.emojiPresent = true;
        this.emoji = emoji;
    }

    public boolean isNamePresent() {
        return namePresent;
    }

    public String name() {
        return name;
    }

    public boolean isEmojiPresent() {
        return emojiPresent;
    }

    public String emoji() {
        return emoji;
    }

    public boolean hasAnyField() {
        return namePresent || emojiPresent;
    }
}
