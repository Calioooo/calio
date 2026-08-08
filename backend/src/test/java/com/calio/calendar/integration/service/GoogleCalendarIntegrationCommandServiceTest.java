package com.calio.calendar.integration.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarIntegrationCommandServiceTest {

    private static final long SYNC_LEASE_DURATION_SECONDS = 300L;

    private final GoogleCalendarIntegrationRepository integrationRepository =
            mock(GoogleCalendarIntegrationRepository.class);
    private final GoogleCalendarIntegrationCommandService commandService =
            new GoogleCalendarIntegrationCommandService(integrationRepository);

    @Test
    @DisplayName("sync lease 획득은 Command Service가 소유한 유효 기간을 Repository에 전달한다")
    void givenAvailableSyncLease_whenAcquire_thenPassesCommandOwnedDuration() {
        // given
        when(integrationRepository.acquireSyncLease(1L, "sync-run", SYNC_LEASE_DURATION_SECONDS))
                .thenReturn(1);

        // when
        commandService.acquireSyncLease(1L, "sync-run");

        // then
        verify(integrationRepository).acquireSyncLease(
                eq(1L),
                eq("sync-run"),
                eq(SYNC_LEASE_DURATION_SECONDS)
        );
    }

    @Test
    @DisplayName("sync lease 연장은 Command Service가 소유한 유효 기간을 Repository에 전달한다")
    void givenOwnedSyncLease_whenRenew_thenPassesCommandOwnedDuration() {
        // given
        when(integrationRepository.extendSyncLease(10L, "sync-run", SYNC_LEASE_DURATION_SECONDS))
                .thenReturn(1);

        // when
        commandService.renewSyncLease(10L, "sync-run");

        // then
        verify(integrationRepository).extendSyncLease(
                eq(10L),
                eq("sync-run"),
                eq(SYNC_LEASE_DURATION_SECONDS)
        );
    }
}
