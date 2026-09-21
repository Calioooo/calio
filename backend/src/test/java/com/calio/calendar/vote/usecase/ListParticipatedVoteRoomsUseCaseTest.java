package com.calio.calendar.vote.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListParticipatedVoteRoomsUseCaseTest {

  private static final Long ACCOUNT_ID = 1L;
  private static final Long FIRST_VOTE_ROOM_ID = 10L;
  private static final Long SECOND_VOTE_ROOM_ID = 20L;

  @Mock private VoteParticipantRepository voteParticipantRepository;
  @Mock private VoteRoomRepository voteRoomRepository;

  @Test
  void givenParticipantsOrderedByUpdatedAt_whenList_thenReturnsTheirVoteRoomsInSameOrder() {
    VoteParticipant firstParticipant =
        VoteParticipant.forAccount(FIRST_VOTE_ROOM_ID, "first", ACCOUNT_ID);
    VoteParticipant secondParticipant =
        VoteParticipant.forAccount(SECOND_VOTE_ROOM_ID, "second", ACCOUNT_ID);
    secondParticipant.submit();
    VoteRoom firstVoteRoom = voteRoom(FIRST_VOTE_ROOM_ID, "첫 번째");
    VoteRoom secondVoteRoom = voteRoom(SECOND_VOTE_ROOM_ID, "두 번째");
    when(voteParticipantRepository.findByAccountIdOrderByUpdatedAtDesc(ACCOUNT_ID))
        .thenReturn(List.of(secondParticipant, firstParticipant));
    when(voteRoomRepository.findAllById(List.of(SECOND_VOTE_ROOM_ID, FIRST_VOTE_ROOM_ID)))
        .thenReturn(List.of(firstVoteRoom, secondVoteRoom));

    var responses =
        new ListParticipatedVoteRoomsUseCase(voteParticipantRepository, voteRoomRepository)
            .list(ACCOUNT_ID);

    assertThat(responses)
        .extracting(response -> response.nickname())
        .containsExactly("second", "first");
    assertThat(responses.getFirst().participantStatus()).isEqualTo(secondParticipant.getStatus());
    assertThat(responses.get(1).participantStatus()).isEqualTo(firstParticipant.getStatus());
    verify(voteParticipantRepository).findByAccountIdOrderByUpdatedAtDesc(ACCOUNT_ID);
    verify(voteRoomRepository).findAllById(List.of(SECOND_VOTE_ROOM_ID, FIRST_VOTE_ROOM_ID));
  }

  private VoteRoom voteRoom(Long id, String name) {
    VoteRoom voteRoom = org.mockito.Mockito.mock(VoteRoom.class);
    when(voteRoom.getId()).thenReturn(id);
    when(voteRoom.getPublicId()).thenReturn(UUID.randomUUID());
    when(voteRoom.getName()).thenReturn(name);
    when(voteRoom.getCandidateStartDate()).thenReturn(LocalDate.of(2026, 8, 14));
    when(voteRoom.getCandidateEndDate()).thenReturn(LocalDate.of(2026, 8, 20));
    return voteRoom;
  }
}
