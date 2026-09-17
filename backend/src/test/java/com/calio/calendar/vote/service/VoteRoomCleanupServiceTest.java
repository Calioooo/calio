package com.calio.calendar.vote.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoteRoomCleanupServiceTest {

    @Mock
    private VoteRoomCommandService voteRoomCommandService;

    @Test
    @DisplayName("VoteRoom 정리는 KST 현재 날짜를 기준으로 90일 보존 경계를 계산한다")
    void givenFixedClock_whenDeleteExpiredVoteRooms_thenDelegatesWithKoreaRetentionCutoff() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-29T15:30:00Z"), ZoneId.of("UTC"));
        VoteRoomCleanupService voteRoomCleanupService = new VoteRoomCleanupService(
                voteRoomCommandService,
                clock
        );
        LocalDate cutoffDate = LocalDate.of(2026, 6, 1);
        when(voteRoomCommandService.deleteExpiredVoteRoomsBefore(cutoffDate)).thenReturn(1);

        voteRoomCleanupService.deleteExpiredVoteRooms();

        verify(voteRoomCommandService).deleteExpiredVoteRoomsBefore(cutoffDate);
    }
}
