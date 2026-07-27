package com.calio.calendar.groupspace.controller.dto;

public class UpdateGroupSpaceRequest {

    private String name;
    private String emoji;
    private boolean namePresent;
    private boolean emojiPresent;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
        this.emojiPresent = true;
    }

    public boolean isNamePresent() {
        return namePresent;
    }

    public boolean isEmojiPresent() {
        return emojiPresent;
    }
}
