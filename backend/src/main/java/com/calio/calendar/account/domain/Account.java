package com.calio.calendar.account.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Embedded
  private AccountNotificationSettings notificationSettings = AccountNotificationSettings.defaults();

  public Account() {}

  public Long getId() {
    return id;
  }

  public AccountNotificationSettings getNotificationSettings() {
    return notificationSettings;
  }

  public void changeNotificationSettings(AccountNotificationSettings notificationSettings) {
    this.notificationSettings = Objects.requireNonNull(notificationSettings);
  }
}
