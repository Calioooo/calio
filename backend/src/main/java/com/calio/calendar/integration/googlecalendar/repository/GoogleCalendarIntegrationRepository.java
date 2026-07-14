package com.calio.calendar.integration.googlecalendar.repository;

import com.calio.calendar.integration.googlecalendar.domain.GoogleCalendarIntegration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleCalendarIntegrationRepository extends JpaRepository<GoogleCalendarIntegration, Long> {

    Optional<GoogleCalendarIntegration> findByAccountId(Long accountId);

    void deleteByAccountId(Long accountId);

    boolean existsByAccountId(Long accountId);
}
