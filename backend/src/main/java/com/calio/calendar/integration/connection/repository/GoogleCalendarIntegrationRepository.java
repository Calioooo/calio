package com.calio.calendar.integration.connection.repository;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarIntegrationRepository extends JpaRepository<GoogleCalendarIntegration, Long> {

    Optional<GoogleCalendarIntegration> findByAccountId(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select integration from GoogleCalendarIntegration integration where integration.accountId = :accountId")
    Optional<GoogleCalendarIntegration> findByAccountIdForUpdate(@Param("accountId") Long accountId);

    boolean existsByAccountId(Long accountId);
}
