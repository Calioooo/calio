package com.calio.calendar.notification.domain;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;

@Entity
@Table(name = "account_notification_settings")
public class AccountNotificationSettings extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Column(nullable = false)
    private boolean calendarNotificationsEnabled = true;

    private Integer timedReminderMinutes = 10;
    private Integer importantReminderMinutes = 120;

    @Column(nullable = false)
    private LocalTime allDayReminderTime = LocalTime.of(9, 0);

    @Column(nullable = false)
    private boolean dailyBriefingEnabled;

    @Column(nullable = false)
    private LocalTime dailyBriefingTime = LocalTime.of(8, 0);

    protected AccountNotificationSettings() {
    }

    public AccountNotificationSettings(Account account) {
        this.account = account;
    }

    public void update(
            boolean calendarNotificationsEnabled,
            Integer timedReminderMinutes,
            Integer importantReminderMinutes,
            LocalTime allDayReminderTime,
            boolean dailyBriefingEnabled,
            LocalTime dailyBriefingTime
    ) {
        this.calendarNotificationsEnabled = calendarNotificationsEnabled;
        this.timedReminderMinutes = timedReminderMinutes;
        this.importantReminderMinutes = importantReminderMinutes;
        this.allDayReminderTime = allDayReminderTime;
        this.dailyBriefingEnabled = dailyBriefingEnabled;
        this.dailyBriefingTime = dailyBriefingTime;
    }

    public Long getAccountId() {
        return account.getId();
    }

    public boolean isCalendarNotificationsEnabled() {
        return calendarNotificationsEnabled;
    }

    public Integer getTimedReminderMinutes() {
        return timedReminderMinutes;
    }

    public Integer getImportantReminderMinutes() {
        return importantReminderMinutes;
    }

    public LocalTime getAllDayReminderTime() {
        return allDayReminderTime;
    }

    public boolean isDailyBriefingEnabled() {
        return dailyBriefingEnabled;
    }

    public LocalTime getDailyBriefingTime() {
        return dailyBriefingTime;
    }
}
