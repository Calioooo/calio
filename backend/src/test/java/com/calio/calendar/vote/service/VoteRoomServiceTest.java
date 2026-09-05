package com.calio.calendar.vote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.CreateVoteRoomRequest;
import com.calio.calendar.vote.domain.VoteRoom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoteRoomServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final LocalDate KOREA_TODAY = LocalDate.of(2026, 8, 14);

    @Mock private VoteRoomQueryService voteRoomQueryService;
    @Mock private VoteRoomCommandService voteRoomCommandService;
    @Mock private AccountQueryService accountQueryService;

    private VoteRoomService voteRoomService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T15:00:00Z"), ZoneOffset.UTC);
        voteRoomService = new VoteRoomService(
                voteRoomQueryService,
                voteRoomCommandService,
                accountQueryService,
                clock
        );
    }

    @Test
    @DisplayName("VoteRoom 생성은 KST 오늘을 시작일로 하고 포함 31일 후보 기간을 허용한다")
    void givenMaximumCandidatePeriod_whenCreate_thenCreatesPublicVoteRoomForAuthenticatedAccount() {
        Account account = new Account();
        when(accountQueryService.getAccount(ACCOUNT_ID)).thenReturn(account);
        when(voteRoomCommandService.create(any(VoteRoom.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        voteRoomService.create(ACCOUNT_ID, new CreateVoteRoomRequest("여행 일정", KOREA_TODAY.plusDays(30)));

        ArgumentCaptor<VoteRoom> voteRoomCaptor = ArgumentCaptor.forClass(VoteRoom.class);
        verify(voteRoomCommandService).create(voteRoomCaptor.capture());
        VoteRoom voteRoom = voteRoomCaptor.getValue();
        assertThat(voteRoom.getPublicId()).isNotNull();
        assertThat(voteRoom.getCandidateStartDate()).isEqualTo(KOREA_TODAY);
        assertThat(voteRoom.getCandidateEndDate()).isEqualTo(KOREA_TODAY.plusDays(30));
        assertThat(voteRoom.getCreatedByAccount()).isSameAs(account);
    }

    @Test
    @DisplayName("후보 종료일이 KST 시작일보다 이르면 VoteRoom을 생성하지 않는다")
    void givenEndDateBeforeKoreaToday_whenCreate_thenRejectsBeforeAccountLookup() {
        assertThatThrownBy(() -> voteRoomService.create(
                ACCOUNT_ID,
                new CreateVoteRoomRequest("여행 일정", KOREA_TODAY.minusDays(1))
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED)
        );

        verify(accountQueryService, never()).getAccount(ACCOUNT_ID);
        verify(voteRoomCommandService, never()).create(any());
    }

    @Test
    @DisplayName("후보 기간이 포함 31일을 초과하면 VoteRoom을 생성하지 않는다")
    void givenCandidatePeriodOverThirtyOneDays_whenCreate_thenRejectsBeforeAccountLookup() {
        assertThatThrownBy(() -> voteRoomService.create(
                ACCOUNT_ID,
                new CreateVoteRoomRequest("여행 일정", KOREA_TODAY.plusDays(31))
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED)
        );

        verify(accountQueryService, never()).getAccount(ACCOUNT_ID);
        verify(voteRoomCommandService, never()).create(any());
    }
}
