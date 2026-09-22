package com.calio.calendar.vote.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantStatus;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class VoteParticipantUseCaseTest {

  private static final UUID VOTE_ROOM_PUBLIC_ID =
      UUID.fromString("7ab6b7d8-11cd-4ce2-83e3-b81ad87ea3c9");
  private static final Long VOTE_ROOM_ID = 1L;
  private static final Long ACCOUNT_ID = 2L;

  @Mock private VoteRoomRepository voteRoomRepository;
  @Mock private VoteParticipantRepository voteParticipantRepository;
  @Mock private VoteRepository voteRepository;

  private PasswordEncoder passwordEncoder;
  private VoteParticipantCredentialVerifier credentialVerifier;
  private CreateVoteParticipantUseCase createVoteParticipantUseCase;
  private SubmitVoteUseCase submitVoteUseCase;
  private LookupVoteParticipantSelectionUseCase lookupVoteParticipantSelectionUseCase;

  @BeforeEach
  void setUp() {
    passwordEncoder = org.mockito.Mockito.spy(new BCryptPasswordEncoder());
    credentialVerifier =
        org.mockito.Mockito.spy(new VoteParticipantCredentialVerifier(passwordEncoder));
    createVoteParticipantUseCase =
        new CreateVoteParticipantUseCase(
            voteRoomRepository, voteParticipantRepository, passwordEncoder);
    submitVoteUseCase =
        new SubmitVoteUseCase(
            voteParticipantRepository, voteRepository, voteRoomRepository, credentialVerifier);
    lookupVoteParticipantSelectionUseCase =
        new LookupVoteParticipantSelectionUseCase(
            voteRoomRepository, voteParticipantRepository, voteRepository, credentialVerifier);
  }

  @Test
  @DisplayName("새 참여자는 VoteRoom ID를 참조하는 REGISTERED 상태로 생성된다")
  void givenAvailableNicknameWithoutPassword_whenCreate_thenCreatesRegisteredParticipant() {
    VoteRoom voteRoom = org.mockito.Mockito.mock(VoteRoom.class);
    when(voteRoom.getId()).thenReturn(VOTE_ROOM_ID);
    when(voteRoomRepository.findForUpdateByPublicId(VOTE_ROOM_PUBLIC_ID))
        .thenReturn(Optional.of(voteRoom));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio"))
        .thenReturn(Optional.empty());
    when(voteParticipantRepository.save(org.mockito.ArgumentMatchers.any(VoteParticipant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    VoteParticipant participant =
        createVoteParticipantUseCase.createParticipantForNonCalioUser(
            VOTE_ROOM_PUBLIC_ID, "calio", null);

    ArgumentCaptor<VoteParticipant> captor = ArgumentCaptor.forClass(VoteParticipant.class);
    verify(voteParticipantRepository).save(captor.capture());
    assertThat(participant).isSameAs(captor.getValue());
    assertThat(participant.getVoteRoomId()).isEqualTo(VOTE_ROOM_ID);
    assertThat(participant.getNickname()).isEqualTo("calio");
    assertThat(participant.getPasswordHash()).isNull();
    assertThat(participant.getStatus()).isEqualTo(VoteParticipantStatus.REGISTERED);
  }

  @Test
  @DisplayName("비밀번호가 있는 새 참여자는 원문 대신 BCrypt 해시를 저장한다")
  void givenAvailableNicknameWithPassword_whenCreate_thenStoresPasswordHashOnly() {
    VoteRoom voteRoom = voteRoom();
    when(voteRoomRepository.findForUpdateByPublicId(VOTE_ROOM_PUBLIC_ID))
        .thenReturn(Optional.of(voteRoom));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio"))
        .thenReturn(Optional.empty());
    when(voteParticipantRepository.save(org.mockito.ArgumentMatchers.any(VoteParticipant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    VoteParticipant participant =
        createVoteParticipantUseCase.createParticipantForNonCalioUser(
            VOTE_ROOM_PUBLIC_ID, "calio", "participant-password");

    assertThat(participant.getPasswordHash()).isNotEqualTo("participant-password");
    assertThat(passwordEncoder.matches("participant-password", participant.getPasswordHash()))
        .isTrue();
  }

  @Test
  @DisplayName("분해형 한글 닉네임은 NFC로 정규화한 값으로 조회하고 저장한다")
  void givenDecomposedKoreanNickname_whenCreate_thenUsesNfcNormalizedNickname() {
    VoteRoom voteRoom = voteRoom();
    String normalizedNickname = "캘리오";
    String decomposedNickname = Normalizer.normalize(normalizedNickname, Normalizer.Form.NFD);
    when(voteRoomRepository.findForUpdateByPublicId(VOTE_ROOM_PUBLIC_ID))
        .thenReturn(Optional.of(voteRoom));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndNickname(
            VOTE_ROOM_PUBLIC_ID, normalizedNickname))
        .thenReturn(Optional.empty());
    when(voteParticipantRepository.save(org.mockito.ArgumentMatchers.any(VoteParticipant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    VoteParticipant participant =
        createVoteParticipantUseCase.createParticipantForNonCalioUser(
            VOTE_ROOM_PUBLIC_ID, decomposedNickname, null);

    verify(voteParticipantRepository)
        .findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, normalizedNickname);
    assertThat(participant.getNickname()).isEqualTo(normalizedNickname);
  }

  @Test
  @DisplayName("같은 VoteRoom의 닉네임은 대소문자와 무관하게 중복 생성할 수 없다")
  void givenDuplicateNickname_whenCreate_thenRejectsBeforeSaving() {
    VoteRoom voteRoom = voteRoom();
    when(voteRoomRepository.findForUpdateByPublicId(VOTE_ROOM_PUBLIC_ID))
        .thenReturn(Optional.of(voteRoom));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "Calio"))
        .thenReturn(Optional.of(new VoteParticipant(VOTE_ROOM_ID, "calio", null)));

    assertThatThrownBy(
            () ->
                createVoteParticipantUseCase.createParticipantForNonCalioUser(
                    VOTE_ROOM_PUBLIC_ID, "Calio", "participant-password"))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.VOTE_PARTICIPANT_NICKNAME_CONFLICT));
    verify(voteParticipantRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("참여자 닉네임은 기존 Group Space와 같은 형식 규칙을 적용한다")
  void givenInvalidNickname_whenCreate_thenRejectsBeforeVoteRoomLookup() {
    assertThatThrownBy(
            () ->
                createVoteParticipantUseCase.createParticipantForNonCalioUser(
                    VOTE_ROOM_PUBLIC_ID, "calio-user", null))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    verifyNoInteractions(voteRoomRepository, voteParticipantRepository);
  }

  @Test
  @DisplayName("존재하지 않는 공개 VoteRoom에는 참여자를 생성할 수 없다")
  void givenMissingVoteRoom_whenCreate_thenRejectsBeforeNicknameLookup() {
    when(voteRoomRepository.findForUpdateByPublicId(VOTE_ROOM_PUBLIC_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                createVoteParticipantUseCase.createParticipantForNonCalioUser(
                    VOTE_ROOM_PUBLIC_ID, "calio", null))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VOTE_ROOM_NOT_FOUND));

    verify(voteParticipantRepository, never())
        .findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio");
  }

  @Test
  @DisplayName("잘못된 비밀번호의 투표 제출은 참여자 잠금과 Vote 교체를 실행하지 않는다")
  void givenInvalidPassword_whenSubmitVotes_thenRejectsBeforeAcquiringLock() {
    VoteParticipant participant =
        new VoteParticipant(VOTE_ROOM_ID, "calio", passwordEncoder.encode("secret"));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio"))
        .thenReturn(Optional.of(participant));

    assertThatThrownBy(
            () ->
                submitVoteUseCase.submitForNonCalioUser(
                    VOTE_ROOM_PUBLIC_ID, "calio", "wrong", List.of(LocalDate.of(2026, 8, 15))))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
    verify(voteParticipantRepository, never())
        .findByVoteRoomPublicIdAndNicknameForUpdate(VOTE_ROOM_PUBLIC_ID, "calio");
    verifyNoInteractions(voteRepository);
  }

  @Test
  @DisplayName("투표 제출은 비잠금 인증을 마친 뒤 참여자 쓰기 잠금을 획득한다")
  void givenValidCredential_whenSubmitVotes_thenVerifiesCredentialBeforeAcquiringLock() {
    VoteParticipant participant =
        new VoteParticipant(VOTE_ROOM_ID, "calio", passwordEncoder.encode("secret"));
    VoteRoom voteRoom = voteRoom();
    when(voteParticipantRepository.findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio"))
        .thenReturn(Optional.of(participant));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndNicknameForUpdate(
            VOTE_ROOM_PUBLIC_ID, "calio"))
        .thenReturn(Optional.of(participant));
    when(voteRoomRepository.findById(VOTE_ROOM_ID)).thenReturn(Optional.of(voteRoom));

    submitVoteUseCase.submitForNonCalioUser(
        VOTE_ROOM_PUBLIC_ID, "calio", "secret", List.of(LocalDate.of(2026, 8, 15)));

    InOrder inOrder =
        inOrder(voteParticipantRepository, credentialVerifier, voteRoomRepository, voteRepository);
    inOrder
        .verify(voteParticipantRepository)
        .findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio");
    inOrder.verify(credentialVerifier).verify(participant, "secret");
    inOrder
        .verify(voteParticipantRepository)
        .findByVoteRoomPublicIdAndNicknameForUpdate(VOTE_ROOM_PUBLIC_ID, "calio");
    inOrder.verify(voteRoomRepository).findById(VOTE_ROOM_ID);
    inOrder.verify(voteRepository).deleteAllByVoteParticipantId(participant.getId());
    assertThat(participant.getStatus()).isEqualTo(VoteParticipantStatus.SUBMITTED);
  }

  @Test
  @DisplayName("인증 사용자의 투표 제출은 accountId로 참여자를 잠그고 비밀번호를 검증하지 않는다")
  void givenAccountParticipant_whenSubmitVotes_thenUsesAccountIdWithoutCredentialVerification() {
    VoteParticipant participant = VoteParticipant.forAccount(VOTE_ROOM_ID, "calio", ACCOUNT_ID);
    VoteRoom voteRoom = voteRoom();
    when(voteParticipantRepository.findByVoteRoomPublicIdAndAccountIdForUpdate(
            VOTE_ROOM_PUBLIC_ID, ACCOUNT_ID))
        .thenReturn(Optional.of(participant));
    when(voteRoomRepository.findById(VOTE_ROOM_ID)).thenReturn(Optional.of(voteRoom));

    submitVoteUseCase.submitForCalioUser(
        VOTE_ROOM_PUBLIC_ID, ACCOUNT_ID, List.of(LocalDate.of(2026, 8, 15)));

    verify(voteParticipantRepository)
        .findByVoteRoomPublicIdAndAccountIdForUpdate(VOTE_ROOM_PUBLIC_ID, ACCOUNT_ID);
    verify(voteParticipantRepository, never())
        .findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio");
    verifyNoInteractions(credentialVerifier);
    assertThat(participant.getStatus()).isEqualTo(VoteParticipantStatus.SUBMITTED);
  }

  @Test
  @DisplayName("인증 사용자의 선택 조회는 accountId로 참여자를 찾고 비밀번호를 검증하지 않는다")
  void givenAccountParticipant_whenLookup_thenUsesAccountIdWithoutCredentialVerification() {
    VoteRoom voteRoom = voteRoom();
    VoteParticipant participant = VoteParticipant.forAccount(VOTE_ROOM_ID, "calio", ACCOUNT_ID);
    when(voteRoomRepository.findByPublicId(VOTE_ROOM_PUBLIC_ID)).thenReturn(Optional.of(voteRoom));
    when(voteParticipantRepository.findByVoteRoomPublicIdAndAccountId(
            VOTE_ROOM_PUBLIC_ID, ACCOUNT_ID))
        .thenReturn(Optional.of(participant));

    var response =
        lookupVoteParticipantSelectionUseCase.lookupForCalioUser(VOTE_ROOM_PUBLIC_ID, ACCOUNT_ID);

    assertThat(response.nickname()).isEqualTo("calio");
    assertThat(response.status()).isEqualTo(VoteParticipantStatus.REGISTERED);
    verify(voteParticipantRepository)
        .findByVoteRoomPublicIdAndAccountId(VOTE_ROOM_PUBLIC_ID, ACCOUNT_ID);
    verify(voteParticipantRepository, never())
        .findByVoteRoomPublicIdAndNickname(VOTE_ROOM_PUBLIC_ID, "calio");
    verifyNoInteractions(credentialVerifier);
  }

  private VoteRoom voteRoom() {
    return new VoteRoom(
        VOTE_ROOM_PUBLIC_ID, "여행 일정", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 20), 1L);
  }
}
