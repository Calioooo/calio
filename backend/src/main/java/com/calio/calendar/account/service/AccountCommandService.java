package com.calio.calendar.account.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.domain.ImportantReminderOffset;
import com.calio.calendar.account.domain.TimedReminderOffset;
import com.calio.calendar.account.repository.AccountRepository;
import java.time.LocalTime;
import org.springframework.stereotype.Service;

@Service
public class AccountCommandService {

    private final AccountRepository accountRepository;

    public AccountCommandService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account createAccount() {
        return accountRepository.save(new Account());
    }

    public AccountNotificationSettings updateNotificationSettings(
            Account account,
            boolean calendarNotificationsEnabled,
            TimedReminderOffset timedReminderOffset,
            ImportantReminderOffset importantReminderOffset,
            LocalTime allDayReminderTime,
            boolean dailyBriefingEnabled,
            LocalTime dailyBriefingTime
    ) {
        account.updateNotificationSettings(
                calendarNotificationsEnabled,
                timedReminderOffset,
                importantReminderOffset,
                allDayReminderTime,
                dailyBriefingEnabled,
                dailyBriefingTime
        );
        return accountRepository.saveAndFlush(account).getNotificationSettings();
    }
}
