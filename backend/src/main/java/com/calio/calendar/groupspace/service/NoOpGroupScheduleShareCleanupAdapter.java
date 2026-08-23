package com.calio.calendar.groupspace.service;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class NoOpGroupScheduleShareCleanupAdapter implements GroupScheduleShareCleanupPort {

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanupMemberShares(Long groupSpaceId, Long accountId) {
        // The group schedule aggregate is not available yet.
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanupGroupShares(Long groupSpaceId) {
        // The group schedule aggregate is not available yet.
    }
}
