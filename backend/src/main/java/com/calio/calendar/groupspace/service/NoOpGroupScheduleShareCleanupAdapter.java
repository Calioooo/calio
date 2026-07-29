package com.calio.calendar.groupspace.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NoOpGroupScheduleShareCleanupAdapter implements GroupScheduleShareCleanupPort {

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanupMemberShares(Long groupSpaceId, Long memberId) {
        // The group schedule aggregate is not available yet.
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanupGroupShares(Long groupSpaceId) {
        // The group schedule aggregate is not available yet.
    }
}
