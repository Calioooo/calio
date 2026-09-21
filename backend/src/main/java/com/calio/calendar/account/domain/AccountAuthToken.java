package com.calio.calendar.account.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class AccountAuthToken {

  @Column(name = "auth_token_hash", length = 64, unique = true)
  private String tokenHash;

  @Column(name = "auth_token_revoked_at")
  private Instant revokedAt;

  @Column(name = "auth_token_last_used_at")
  private Instant lastUsedAt;

  protected AccountAuthToken() {}

  private AccountAuthToken(String tokenHash) {
    this.tokenHash = Objects.requireNonNull(tokenHash);
  }

  static AccountAuthToken issue(String tokenHash) {
    return new AccountAuthToken(tokenHash);
  }

  void authenticate(Instant usedAt) {
    if (revokedAt != null) {
      throw new CalioException(ErrorCode.AUTH_TOKEN_REVOKED);
    }
    lastUsedAt = Objects.requireNonNull(usedAt);
  }

  void revoke(Instant revokedAt) {
    this.revokedAt = Objects.requireNonNull(revokedAt);
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }
}
