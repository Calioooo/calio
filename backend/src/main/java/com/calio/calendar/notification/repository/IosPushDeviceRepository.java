package com.calio.calendar.notification.repository;

import com.calio.calendar.notification.domain.IosPushDevice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IosPushDeviceRepository extends JpaRepository<IosPushDevice, Long> {
    Optional<IosPushDevice> findByAccount_IdAndInstallationId(Long accountId, String installationId);

    Optional<IosPushDevice> findByApnsToken(String apnsToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from IosPushDevice device where device.apnsToken = :apnsToken")
    Optional<IosPushDevice> lockDeviceWithToken(@Param("apnsToken") String apnsToken);

    List<IosPushDevice> findByAccount_IdAndActiveTrueAndApnsTokenIsNotNull(Long accountId);
}
