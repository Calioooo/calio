package com.calio.calendar.account.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Embedded private AccountAuthToken authToken;

  public Account() {}

  public Long getId() {
    return id;
  }

  public void issueAuthToken(String tokenHash) {
    authToken = AccountAuthToken.issue(tokenHash);
  }

  public void authenticateAuthToken(Instant usedAt) {
    authToken.authenticate(usedAt);
  }

  public void revokeAuthToken(Instant revokedAt) {
    authToken.revoke(revokedAt);
  }

  public String getAuthTokenHash() {
    return authToken == null ? null : authToken.getTokenHash();
  }

  public Instant getAuthTokenRevokedAt() {
    return authToken == null ? null : authToken.getRevokedAt();
  }

  public Instant getAuthTokenLastUsedAt() {
    return authToken == null ? null : authToken.getLastUsedAt();
  }
}
