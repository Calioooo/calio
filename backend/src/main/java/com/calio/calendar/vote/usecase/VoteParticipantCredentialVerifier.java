package com.calio.calendar.vote.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteParticipant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class VoteParticipantCredentialVerifier {

  private final PasswordEncoder passwordEncoder;

  public VoteParticipantCredentialVerifier(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

  public void verify(VoteParticipant participant, String password) {
    if (participant.hasPassword()
        && (password == null || !passwordEncoder.matches(password, participant.getPasswordHash()))) {
      throw new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID);
    }
  }
}
