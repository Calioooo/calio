package com.calio.calendar.notification.repository;

import com.calio.calendar.notification.domain.IosNotificationAuthorizationStatus;
import com.calio.calendar.notification.domain.IosNotificationEndpoint;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IosNotificationEndpointRepository extends JpaRepository<IosNotificationEndpoint, Long> {
    Optional<IosNotificationEndpoint> findByAccount_IdAndInstallationId(Long accountId, String installationId);

    Optional<IosNotificationEndpoint> findByApnsToken(String apnsToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select endpoint from IosNotificationEndpoint endpoint where endpoint.apnsToken = :apnsToken")
    Optional<IosNotificationEndpoint> lockEndpointWithToken(@Param("apnsToken") String apnsToken);

    List<IosNotificationEndpoint> findByAccount_IdAndActiveTrueAndAuthorizationStatus(Long accountId, IosNotificationAuthorizationStatus status);
}
