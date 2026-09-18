package com.calio.calendar.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountAuthTokenTest {

  @Test
  @DisplayName("활성 인증 토큰은 인증 시 마지막 사용 시각을 변경한다")
  void givenActiveToken_whenAuthenticate_thenUpdatesLastUsedAt() {
    AccountAuthToken authToken = new AccountAuthToken(new Account(), "token-hash");
    Instant usedAt = Instant.parse("2026-09-19T00:00:00Z");

    authToken.authenticate(usedAt);

    assertThat(authToken.getLastUsedAt()).isEqualTo(usedAt);
  }

  @Test
  @DisplayName("폐기된 인증 토큰은 사용할 수 없고 마지막 사용 시각도 변경되지 않는다")
  void givenRevokedToken_whenAuthenticate_thenRejectsWithoutUpdatingLastUsedAt() {
    AccountAuthToken authToken = new AccountAuthToken(new Account(), "token-hash");
    authToken.revoke(Instant.parse("2026-09-18T00:00:00Z"));

    assertThatThrownBy(() -> authToken.authenticate(Instant.parse("2026-09-19T00:00:00Z")))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_REVOKED));
    assertThat(authToken.getLastUsedAt()).isNull();
  }
}
