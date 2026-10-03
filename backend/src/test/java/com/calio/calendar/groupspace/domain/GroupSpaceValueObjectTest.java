package com.calio.calendar.groupspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupSpaceValueObjectTest {

  @Test
  @DisplayName("GroupSpaceName은 trim 후 NFC로 정규화하고 Unicode code point 30자까지 허용한다")
  void groupSpaceNameNormalizesTrimAndNfcUsingCodePointLength() {
    GroupSpaceName name = new GroupSpaceName("  " + "가".repeat(29) + "e\u0301  ");

    assertThat(name.value()).isEqualTo("가".repeat(29) + "é");
    assertThat(name.value().codePointCount(0, name.value().length())).isEqualTo(30);
  }

  @Test
  @DisplayName("GroupSpaceName은 빈 값, 30 code point 초과, control character를 거부한다")
  void groupSpaceNameRejectsInvalidValues() {
    assertValidationFailed(() -> new GroupSpaceName("  "));
    assertValidationFailed(() -> new GroupSpaceName("가".repeat(31)));
    assertValidationFailed(() -> new GroupSpaceName("group\nname"));
  }

  @Test
  @DisplayName("GroupMemberNickname은 NFC 정규화 후 영문, 숫자, 완성형 한글 1~9자만 허용한다")
  void groupMemberNicknameNormalizesAndValidates() {
    assertThat(new GroupMemberNickname("가123").value()).isEqualTo("가123");
    assertThat(new GroupMemberNickname("Calio123").value()).isEqualTo("Calio123");
    assertValidationFailed(() -> new GroupMemberNickname("nick name"));
    assertValidationFailed(() -> new GroupMemberNickname("nick!"));
    assertValidationFailed(() -> new GroupMemberNickname("Cafe\u0301"));
    assertValidationFailed(() -> new GroupMemberNickname("가".repeat(10)));
  }

  @Test
  @DisplayName("GroupSpaceEmoji는 빈 값을 null로 표현하고 Unicode 원문과 64 code point 제한을 보존한다")
  void groupSpaceEmojiPreservesValueAndValidatesLength() {
    String maximumLengthEmoji = "😀".repeat(64);

    assertThat(GroupSpaceEmoji.fromNullable(null)).isNull();
    assertThat(GroupSpaceEmoji.fromNullable("")).isNull();
    assertThat(GroupSpaceEmoji.fromNullable("👩🏽‍💻").value()).isEqualTo("👩🏽‍💻");
    assertThat(GroupSpaceEmoji.fromNullable(maximumLengthEmoji).value())
        .isEqualTo(maximumLengthEmoji);
    assertValidationFailed(() -> GroupSpaceEmoji.fromNullable("😀".repeat(65)));
  }

  private void assertValidationFailed(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(CalioException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }
}
