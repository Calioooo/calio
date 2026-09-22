package com.calio.calendar.vote.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CreateVoteParticipantUseCaseAccountTest {

  private static final UUID VOTE_ROOM_PUBLIC_ID =
      UUID.fromString("7ab6b7d8-11cd-4ce2-83e3-b81ad87ea3c9");
  private static final Long VOTE_ROOM_ID = 1L;
  private static final Long ACCOUNT_ID = 2L;

  @Mock private VoteRoomRepository voteRoomRepository;
  @Mock private VoteParticipantRepository voteParticipantRepository;
  @Mock private PasswordEncoder passwordEncoder;

  private CreateVoteParticipantUseCase createVoteParticipantUseCase;

  @BeforeEach
  void setUp() {
    createVoteParticipantUseCase =
        new CreateVoteParticipantUseCase(
            voteRoomRepository, voteParticipantRepository, passwordEncoder);
  }

  @Test
  @DisplayName("인증 사용자는 accountId로 식별되고 비밀번호 없이 참여자를 생성한다")
  void givenAvailableAccountAndNickname_whenCreate_thenCreatesAccountParticipantWithoutPassword() {
    VoteRoom voteRoom = org.mockito.Mockito.mock(VoteRoom.class);
    when(voteRoom.getId()).thenReturn(VOTE_ROOM_ID);
    when(voteRoomRepository.findForUpdateByPublicId(VOTE_ROOM_PUBLIC_ID))
        .thenReturn(Optional.of(voteRoom));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndAccountId(
            VOTE_ROOM_PUBLIC_ID, ACCOUNT_ID))
        .thenReturn(Optional.empty());
    when(voteParticipantRepository.findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio"))
        .thenReturn(Optional.empty());
    when(voteParticipantRepository.save(org.mockito.ArgumentMatchers.any(VoteParticipant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    createVoteParticipantUseCase.createForCalioUser(VOTE_ROOM_PUBLIC_ID, ACCOUNT_ID, "calio");

    ArgumentCaptor<VoteParticipant> captor = ArgumentCaptor.forClass(VoteParticipant.class);
    verify(voteParticipantRepository).save(captor.capture());
    assertThat(captor.getValue().getVoteRoomId()).isEqualTo(VOTE_ROOM_ID);
    assertThat(captor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(captor.getValue().getPasswordHash()).isNull();
  }

  @Test
  @DisplayName("동일 accountId와 닉네임이 모두 충돌하면 accountId 중복 오류를 우선한다")
  void givenDuplicateAccountAndNickname_whenCreate_thenRejectsAccountDuplicateFirst() {
    VoteRoom voteRoom = voteRoom();
    when(voteRoomRepository.findForUpdateByPublicId(VOTE_ROOM_PUBLIC_ID))
        .thenReturn(Optional.of(voteRoom));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndAccountId(
            VOTE_ROOM_PUBLIC_ID, ACCOUNT_ID))
        .thenReturn(Optional.of(VoteParticipant.forAccount(VOTE_ROOM_ID, "calio", ACCOUNT_ID)));

    assertThatThrownBy(
            () ->
                createVoteParticipantUseCase.createForCalioUser(
                    VOTE_ROOM_PUBLIC_ID, ACCOUNT_ID, "calio"))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.VOTE_PARTICIPANT_ALREADY_EXISTS));

    verify(voteParticipantRepository, never())
        .findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio");
    verify(voteParticipantRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  private VoteRoom voteRoom() {
    return new VoteRoom(
        VOTE_ROOM_PUBLIC_ID, "여행 일정", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 20), 1L);
  }
}
