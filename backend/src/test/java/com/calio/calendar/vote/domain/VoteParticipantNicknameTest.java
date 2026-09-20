package com.calio.calendar.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoteParticipantNicknameTest {

  @Test
  @DisplayName("분해형 한글 닉네임은 NFC 형태로 정규화한다")
  void givenDecomposedKoreanNickname_whenCreate_thenNormalizesToNfc() {
    String normalizedNickname = "캘리오";
    String decomposedNickname = Normalizer.normalize(normalizedNickname, Normalizer.Form.NFD);

    VoteParticipantNickname nickname = VoteParticipantNickname.of(decomposedNickname);

    assertThat(nickname.value()).isEqualTo(normalizedNickname);
  }

  @Test
  @DisplayName("형식에 맞지 않는 닉네임은 생성할 수 없다")
  void givenInvalidNickname_whenCreate_thenRejects() {
    assertThatThrownBy(() -> VoteParticipantNickname.of("calio-user"))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }
}
