package com.calio.calendar.groupspace.service;

/**
 * Member-scoped dependent data cleanup that runs inside the membership lifecycle transaction.
 * Implementations must run after the member lock is acquired and before the inactive status is persisted.
 */
public interface GroupMemberDeactivationCleanup {

    void deleteByMemberId(Long memberId);
}
