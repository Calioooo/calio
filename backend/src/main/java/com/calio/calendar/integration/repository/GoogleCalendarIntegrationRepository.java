package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface GoogleCalendarIntegrationRepository extends JpaRepository<GoogleCalendarIntegration, Long> {

    Optional<GoogleCalendarIntegration> findByAccountId(Long accountId);

    boolean existsByAccountId(Long accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    void deleteByAccountId(Long accountId);
}
