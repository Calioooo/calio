package com.calio.calendar.vote.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
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
  @DisplayName("인증 사용자는 accountId를 연결하고 선택적 password hash와 함께 참여자를 생성한다")
  void
      givenAvailableAccountNicknameAndPassword_whenCreate_thenCreatesAccountParticipantWithPasswordHash() {
    VoteRoom voteRoom = org.mockito.Mockito.mock(VoteRoom.class);
    when(voteRoom.getId()).thenReturn(VOTE_ROOM_ID);
    when(voteRoomRepository.findForUpdateByPublicId(VOTE_ROOM_PUBLIC_ID))
        .thenReturn(Optional.of(voteRoom));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio"))
        .thenReturn(Optional.empty());
    when(passwordEncoder.encode("secret")).thenReturn("hashed-password");
    when(voteParticipantRepository.save(org.mockito.ArgumentMatchers.any(VoteParticipant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    createVoteParticipantUseCase.create(VOTE_ROOM_PUBLIC_ID, ACCOUNT_ID, "calio", "secret");

    ArgumentCaptor<VoteParticipant> captor = ArgumentCaptor.forClass(VoteParticipant.class);
    verify(voteParticipantRepository).save(captor.capture());
    assertThat(captor.getValue().getVoteRoomId()).isEqualTo(VOTE_ROOM_ID);
    assertThat(captor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
  }
}
