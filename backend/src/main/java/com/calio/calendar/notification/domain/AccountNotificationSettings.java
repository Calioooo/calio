package com.calio.calendar.notification.domain;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimedReminderOffset timedReminderOffset = TimedReminderOffset.MINUTES_10;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportantReminderOffset importantReminderOffset = ImportantReminderOffset.MINUTES_120;

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
            TimedReminderOffset timedReminderOffset,
            ImportantReminderOffset importantReminderOffset,
            LocalTime allDayReminderTime,
            boolean dailyBriefingEnabled,
            LocalTime dailyBriefingTime
    ) {
        this.calendarNotificationsEnabled = calendarNotificationsEnabled;
        this.timedReminderOffset = timedReminderOffset;
        this.importantReminderOffset = importantReminderOffset;
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

    public TimedReminderOffset getTimedReminderOffset() {
        return timedReminderOffset;
    }

    public ImportantReminderOffset getImportantReminderOffset() {
        return importantReminderOffset;
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
