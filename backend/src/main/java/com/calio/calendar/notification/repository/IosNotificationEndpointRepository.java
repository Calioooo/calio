package com.calio.calendar.notification.repository;

import com.calio.calendar.notification.domain.IosNotificationAuthorizationStatus;
import com.calio.calendar.notification.domain.IosNotificationEndpoint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IosNotificationEndpointRepository extends JpaRepository<IosNotificationEndpoint, Long> {
    Optional<IosNotificationEndpoint> findByAccount_IdAndInstallationId(Long accountId, String installationId);
    Optional<IosNotificationEndpoint> findByApnsToken(String apnsToken);
    List<IosNotificationEndpoint> findByAccount_IdAndActiveTrueAndAuthorizationStatus(Long accountId, IosNotificationAuthorizationStatus status);
}
