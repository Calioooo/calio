package com.calio.calendar.vote.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class VoteParticipantCredentialVerifierTest {

  @Mock private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("비밀번호가 있는 참여자는 일치하는 비밀번호를 제공해야 한다")
  void givenPasswordProtectedParticipant_whenVerify_thenRequiresMatchingPassword() {
    VoteParticipant participant = new VoteParticipant(voteRoom(), "calio", "hashed-password");
    when(passwordEncoder.matches("secret", "hashed-password")).thenReturn(true);
    VoteParticipantCredentialVerifier credentialVerifier =
        new VoteParticipantCredentialVerifier(passwordEncoder);

    credentialVerifier.verify(participant, "secret");

    verify(passwordEncoder).matches("secret", "hashed-password");
  }

  @Test
  @DisplayName("비밀번호가 없는 참여자는 비밀번호 검증 없이 통과한다")
  void givenParticipantWithoutPassword_whenVerify_thenSkipsPasswordMatching() {
    VoteParticipantCredentialVerifier credentialVerifier =
        new VoteParticipantCredentialVerifier(passwordEncoder);

    credentialVerifier.verify(new VoteParticipant(voteRoom(), "calio", null), null);

    verify(passwordEncoder, never()).matches(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("비밀번호가 없거나 일치하지 않으면 자격증명 오류를 반환한다")
  void givenInvalidPassword_whenVerify_thenRejects() {
    VoteParticipantCredentialVerifier credentialVerifier =
        new VoteParticipantCredentialVerifier(passwordEncoder);
    VoteParticipant participant = new VoteParticipant(voteRoom(), "calio", "hashed-password");
    when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

    assertThatThrownBy(() -> credentialVerifier.verify(participant, null))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
    assertThatThrownBy(() -> credentialVerifier.verify(participant, "wrong"))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
  }

  private VoteRoom voteRoom() {
    return new VoteRoom(
        UUID.randomUUID(),
        "여행 일정",
        LocalDate.of(2026, 8, 14),
        LocalDate.of(2026, 8, 20),
        1L);
  }
}
