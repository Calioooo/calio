package com.calio.calendar.groupspace.service;

/**
 * Group-scoped dependent data cleanup that runs inside the caller's delete transaction.
 * Implementations must not start a new transaction or perform asynchronous cleanup.
 */
public interface GroupSpaceDeletionCleanup {

    void deleteByGroupSpaceId(Long groupSpaceId);
}
