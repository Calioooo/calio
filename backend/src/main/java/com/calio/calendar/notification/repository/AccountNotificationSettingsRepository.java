package com.calio.calendar.notification.repository;
import com.calio.calendar.notification.domain.AccountNotificationSettings;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AccountNotificationSettingsRepository extends JpaRepository<AccountNotificationSettings, Long> {
    Optional<AccountNotificationSettings> findByAccount_Id(Long accountId);
    List<AccountNotificationSettings> findByCalendarNotificationsEnabledTrue();
}
