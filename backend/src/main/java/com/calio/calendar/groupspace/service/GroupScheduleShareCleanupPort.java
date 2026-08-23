package com.calio.calendar.groupspace.service;

/**
 * Membership lifecycle boundary for group schedule shares. Implementations participate in the
 * caller transaction and must complete before dependent membership or group data is changed.
 */
public interface GroupScheduleShareCleanupPort {

    void cleanupMemberShares(Long groupSpaceId, Long accountId);

    void cleanupGroupShares(Long groupSpaceId);
}
