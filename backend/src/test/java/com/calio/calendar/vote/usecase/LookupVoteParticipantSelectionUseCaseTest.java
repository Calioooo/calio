package com.calio.calendar.vote.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantStatus;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LookupVoteParticipantSelectionUseCaseTest {

  private static final UUID VOTE_ROOM_PUBLIC_ID =
      UUID.fromString("7ab6b7d8-11cd-4ce2-83e3-b81ad87ea3c9");

  @Mock private VoteRoomRepository voteRoomRepository;

  @Mock private VoteParticipantRepository voteParticipantRepository;

  @Mock private VoteRepository voteRepository;

  private LookupVoteParticipantSelectionUseCase lookupVoteParticipantSelectionUseCase;
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder();
    lookupVoteParticipantSelectionUseCase =
        new LookupVoteParticipantSelectionUseCase(
            voteRoomRepository,
            voteParticipantRepository,
            voteRepository,
            new VoteParticipantCredentialVerifier(passwordEncoder));
  }

  @Test
  @DisplayName("제출한 참여자의 기존 불가능한 날짜 선택을 날짜순으로 복원한다")
  void givenSubmittedParticipant_whenLookup_thenReturnsSavedUnavailableDates() {
    // given
    VoteRoom voteRoom = voteRoom();
    VoteParticipant participant =
        new VoteParticipant(voteRoom.getId(), "calio", passwordEncoder.encode("secret"));
    participant.submit();
    when(voteRoomRepository.findByPublicId(VOTE_ROOM_PUBLIC_ID)).thenReturn(Optional.of(voteRoom));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio"))
        .thenReturn(Optional.of(participant));
    when(voteRepository.findByVoteParticipantIdOrderByUnavailableDateAsc(participant.getId()))
        .thenReturn(
            List.of(
                new Vote(participant, LocalDate.of(2026, 8, 15)),
                new Vote(participant, LocalDate.of(2026, 8, 17))));

    // when
    var response =
        lookupVoteParticipantSelectionUseCase.lookupForNonCalioUser(
            VOTE_ROOM_PUBLIC_ID, "calio", "secret");

    // then
    assertThat(response.nickname()).isEqualTo("calio");
    assertThat(response.status()).isEqualTo(VoteParticipantStatus.SUBMITTED);
    assertThat(response.unavailableDates())
        .containsExactly(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 17));
  }

  @Test
  @DisplayName("제출 전 REGISTERED 참여자의 기존 선택 복원은 Vote 조회 없이 빈 날짜 목록을 반환한다")
  void givenRegisteredParticipant_whenLookup_thenReturnsEmptyUnavailableDates() {
    // given
    VoteRoom voteRoom = voteRoom();
    VoteParticipant participant = new VoteParticipant(voteRoom.getId(), "calio", null);
    when(voteRoomRepository.findByPublicId(VOTE_ROOM_PUBLIC_ID)).thenReturn(Optional.of(voteRoom));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio"))
        .thenReturn(Optional.of(participant));

    // when
    var response =
        lookupVoteParticipantSelectionUseCase.lookupForNonCalioUser(
            VOTE_ROOM_PUBLIC_ID, "calio", null);

    // then
    assertThat(response.status()).isEqualTo(VoteParticipantStatus.REGISTERED);
    assertThat(response.unavailableDates()).isEmpty();
    verify(voteRepository, never())
        .findByVoteParticipantIdOrderByUnavailableDateAsc(participant.getId());
  }

  @Test
  @DisplayName("선택 복원은 존재하지 않는 VoteRoom에 대해 참여자 자격증명을 조회하지 않고 404를 반환한다")
  void givenMissingVoteRoom_whenLookup_thenRejectsBeforeCredentialLookup() {
    // given
    when(voteRoomRepository.findByPublicId(VOTE_ROOM_PUBLIC_ID)).thenReturn(Optional.empty());

    // when, then
    assertThatThrownBy(
            () ->
                lookupVoteParticipantSelectionUseCase.lookupForNonCalioUser(
                    VOTE_ROOM_PUBLIC_ID, "calio", null))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VOTE_ROOM_NOT_FOUND));
    verifyNoInteractions(voteParticipantRepository, voteRepository);
  }

  private VoteRoom voteRoom() {
    return new VoteRoom(
        VOTE_ROOM_PUBLIC_ID, "여행 일정", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 20), 1L);
  }
}
