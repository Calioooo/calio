package com.calio.calendar.groupspace.domain;

public enum GroupMemberRole {
    OWNER,
    MEMBER;

    public boolean isOwner() {
        return this == OWNER;
    }
}
