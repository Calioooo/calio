package com.calio.calendar.groupspace.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class UpdateGroupSpaceRequest {

    private String name;
    private String emoji;
    private boolean namePresent;
    private boolean emojiPresent;

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    @JsonSetter("emoji")
    public void setEmoji(String emoji) {
        this.emoji = emoji;
        this.emojiPresent = true;
    }

    public String getName() {
        return name;
    }

    public String getEmoji() {
        return emoji;
    }

    @JsonIgnore
    public boolean isNamePresent() {
        return namePresent;
    }

    @JsonIgnore
    public boolean isEmojiPresent() {
        return emojiPresent;
    }

    @JsonIgnore
    @AssertTrue
    public boolean isAnyFieldPresent() {
        return namePresent || emojiPresent;
    }
}
