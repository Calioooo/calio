package com.calio.calendar.integration.connection.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarIntegrationCommandServiceTest {

    private final GoogleCalendarIntegrationRepository integrationRepository =
            mock(GoogleCalendarIntegrationRepository.class);
    private final GoogleCalendarIntegrationCommandService commandService =
            new GoogleCalendarIntegrationCommandService(integrationRepository);

    @Test
    @DisplayName("동기화 cursor 저장은 Integration Repository에 전달한다")
    void givenNextSyncToken_whenSaving_thenUpdatesIntegrationCursor() {
        // given
        when(integrationRepository.updateNextSyncToken(1L, "next-token")).thenReturn(1);

        // when
        commandService.saveNextSyncToken(1L, "next-token");

        // then
        verify(integrationRepository).updateNextSyncToken(1L, "next-token");
    }

    @Test
    @DisplayName("동기화 cursor를 저장하지 못하면 sync conflict 예외를 반환한다")
    void givenMissingIntegration_whenSavingCursor_thenThrowsSyncConflict() {
        // given
        when(integrationRepository.updateNextSyncToken(1L, "next-token")).thenReturn(0);

        // when, then
        assertThatThrownBy(() -> commandService.saveNextSyncToken(1L, "next-token"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT));
    }
}
