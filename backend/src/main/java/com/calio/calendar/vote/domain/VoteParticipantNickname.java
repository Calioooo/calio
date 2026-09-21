package com.calio.calendar.vote.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.text.Normalizer;
import java.util.regex.Pattern;

@Embeddable
public class VoteParticipantNickname {

  private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[A-Za-z0-9가-힣]{1,9}$");

  @Column(nullable = false, length = 9)
  private String value;

  protected VoteParticipantNickname() {}

  private VoteParticipantNickname(String value) {
    this.value = value;
  }

  public static VoteParticipantNickname of(String rawNickname) {
    if (rawNickname == null) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    String normalizedNickname = Normalizer.normalize(rawNickname, Normalizer.Form.NFC);
    if (!NICKNAME_PATTERN.matcher(normalizedNickname).matches()) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    return new VoteParticipantNickname(normalizedNickname);
  }

  public String value() {
    return value;
  }
}
