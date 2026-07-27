package com.calio.calendar.groupspace.service;

public interface GroupSpaceDeletionCleanup {

    default void deleteGroupSchedules(Long groupSpaceId) {
    }

    default void deleteGroupInvitations(Long groupSpaceId) {
    }
}
