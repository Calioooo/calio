package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleOperationLeaseServiceTest {

    private static final long LEASE_DURATION_SECONDS = 300L;

    private final GoogleCalendarIntegrationCommandService integrationCommandService =
            mock(GoogleCalendarIntegrationCommandService.class);
    private final GoogleOperationLeaseService leaseService =
            new GoogleOperationLeaseService(integrationCommandService);

    @Test
    @DisplayName("operation lease 획득은 Lease Service가 소유한 유효 기간을 사용한다")
    void givenAvailableLease_whenAcquire_thenUsesOwnedDuration() {
        // given
        when(integrationCommandService.acquireOperationLease(
                1L,
                "worker-token",
                LEASE_DURATION_SECONDS
        )).thenReturn(true);

        // when
        boolean acquired = leaseService.acquire(1L, "worker-token");

        // then
        assertThat(acquired).isTrue();
        verify(integrationCommandService).acquireOperationLease(
                1L,
                "worker-token",
                LEASE_DURATION_SECONDS
        );
    }

    @Test
    @DisplayName("operation lease 연장은 Lease Service가 소유한 유효 기간을 사용한다")
    void givenOwnedLease_whenExtend_thenUsesOwnedDuration() {
        // when
        leaseService.extend(10L, 1L, "worker-token");

        // then
        verify(integrationCommandService).extendOperationLease(
                10L,
                1L,
                "worker-token",
                LEASE_DURATION_SECONDS
        );
    }
}
