package com.calio.calendar.vote.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.vote.service.VoteRoomCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class VoteRoomCleanupSchedulerTest {

    @Mock
    private VoteRoomCleanupService voteRoomCleanupService;

    @Test
    @DisplayName("VoteRoom cleanup scheduler는 만료 VoteRoom 삭제를 service에 위임한다")
    void givenCleanupScheduler_whenDeleteExpiredVoteRooms_thenDelegatesCleanup() {
        VoteRoomCleanupScheduler scheduler = new VoteRoomCleanupScheduler(voteRoomCleanupService);
        when(voteRoomCleanupService.deleteExpiredVoteRooms()).thenReturn(3);

        scheduler.deleteExpiredVoteRooms();

        verify(voteRoomCleanupService).deleteExpiredVoteRooms();
    }

    @Test
    @DisplayName("VoteRoom cleanup scheduler는 매일 KST 04시 30분에 실행된다")
    void deleteExpiredVoteRoomsHasKoreaDailySchedule() throws NoSuchMethodException {
        Scheduled scheduled = VoteRoomCleanupScheduler.class
                .getDeclaredMethod("deleteExpiredVoteRooms")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 30 4 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("VoteRoom cleanup scheduler는 cleanup 실패를 외부로 전파하지 않는다")
    void givenCleanupFailure_whenDeleteExpiredVoteRooms_thenContainsException() {
        VoteRoomCleanupScheduler scheduler = new VoteRoomCleanupScheduler(voteRoomCleanupService);
        doThrow(new RuntimeException("cleanup failed"))
                .when(voteRoomCleanupService)
                .deleteExpiredVoteRooms();

        assertDoesNotThrow(scheduler::deleteExpiredVoteRooms);
    }
}
