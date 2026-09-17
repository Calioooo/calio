package com.calio.calendar.vote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.vote.controller.dto.VoteResultResponse;
import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoteResultServiceTest {

    private static final UUID VOTE_ROOM_PUBLIC_ID = UUID.fromString("7ab6b7d8-11cd-4ce2-83e3-b81ad87ea3c9");

    @Mock
    private VoteResultQueryService voteResultQueryService;

    private VoteResultService voteResultService;

    @BeforeEach
    void setUp() {
        voteResultService = new VoteResultService(voteResultQueryService);
    }

    @Test
    @DisplayName("결과는 후보 기간의 0표 날짜를 보완하고 제출 완료 참여자만 포함한다")
    void givenPartialSubmittedVotes_whenGetResult_thenCompletesCandidateDatesAndExcludesRegisteredParticipant() {
        VoteRoom voteRoom = new VoteRoom(
                VOTE_ROOM_PUBLIC_ID,
                "여행 일정",
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 16),
                new Account()
        );
        when(voteResultQueryService.getVoteRoom(VOTE_ROOM_PUBLIC_ID)).thenReturn(voteRoom);
        VoteParticipant submittedParticipant = new VoteParticipant(voteRoom, "submitted", null);
        submittedParticipant.submit();
        when(voteResultQueryService.listSubmittedVotes(VOTE_ROOM_PUBLIC_ID))
                .thenReturn(List.of(new Vote(submittedParticipant, LocalDate.of(2026, 8, 15))));
        when(voteResultQueryService.listSubmittedNicknames(VOTE_ROOM_PUBLIC_ID))
                .thenReturn(List.of("submitted"));

        VoteResultResponse result = voteResultService.getResult(VOTE_ROOM_PUBLIC_ID);

        assertThat(result.dates()).extracting(date -> date.date())
                .containsExactly(
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 8, 16)
                );
        assertThat(result.dates().get(0).unavailableCount()).isZero();
        assertThat(result.dates().get(0).unavailableNicknames()).isEmpty();
        assertThat(result.dates().get(1).unavailableCount()).isOne();
        assertThat(result.dates().get(1).unavailableNicknames()).containsExactly("submitted");
        assertThat(result.submittedNicknames()).containsExactly("submitted");
    }
}
