package com.calio.calendar.notification.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "ios_push_devices")
public class IosPushDevice extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "installation_id", nullable = false)
  private String installationId;

  @Column(name = "apns_token")
  private String apnsToken;

  @Column(nullable = false)
  private boolean active;

  private Instant deactivatedAt;

  protected IosPushDevice() {}

  public IosPushDevice(Long accountId, String installationId, String apnsToken) {
    this.accountId = Objects.requireNonNull(accountId);
    this.installationId = Objects.requireNonNull(installationId);
    refresh(apnsToken);
  }

  public void refresh(String apnsToken) {
    this.apnsToken = Objects.requireNonNull(apnsToken);
    active = true;
    deactivatedAt = null;
  }

  public boolean belongsToInstallation(Long accountId, String installationId) {
    return Objects.equals(this.accountId, accountId)
        && Objects.equals(this.installationId, installationId);
  }

  public void deactivate(Instant now) {
    active = false;
    apnsToken = null;
    deactivatedAt = Objects.requireNonNull(now);
  }

  public Long getId() {
    return id;
  }

  public String getApnsToken() {
    return apnsToken;
  }

  public boolean isEligible() {
    return active && apnsToken != null;
  }
}
