package com.calio.calendar.vote.usecase;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.vote.repository.VoteRoomRepository;
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
class DeleteExpiredVoteRoomsUseCaseTest {

    @Mock
    private VoteRoomRepository voteRoomRepository;

    @Test
    @DisplayName("VoteRoom 정리는 KST 현재 날짜를 기준으로 90일 보존 경계를 계산한다")
    void givenFixedClock_whenDeleteExpiredVoteRooms_thenDelegatesWithKoreaRetentionCutoff() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-29T15:30:00Z"), ZoneId.of("UTC"));
        DeleteExpiredVoteRoomsUseCase deleteExpiredVoteRoomsUseCase = new DeleteExpiredVoteRoomsUseCase(
                voteRoomRepository,
                clock
        );
        LocalDate cutoffDate = LocalDate.of(2026, 6, 1);
        when(voteRoomRepository.deleteExpiredVoteRoomsBefore(cutoffDate)).thenReturn(1);

        deleteExpiredVoteRoomsUseCase.deleteExpiredVoteRooms();

        verify(voteRoomRepository).deleteExpiredVoteRoomsBefore(cutoffDate);
    }
}
