package com.calio.calendar.notification.repository;

import com.calio.calendar.notification.domain.IosPushDevice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IosPushDeviceRepository extends JpaRepository<IosPushDevice, Long> {
  Optional<IosPushDevice> findByAccountIdAndInstallationId(Long accountId, String installationId);

  Optional<IosPushDevice> findByApnsToken(String apnsToken);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select device from IosPushDevice device where device.apnsToken = :apnsToken")
  Optional<IosPushDevice> lockDeviceWithToken(@Param("apnsToken") String apnsToken);

  List<IosPushDevice> findByAccountIdAndActiveTrueAndApnsTokenIsNotNull(Long accountId);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update IosPushDevice device
      set device.active = false,
          device.apnsToken = null,
          device.deactivatedAt = :deactivatedAt,
          device.updatedAt = :deactivatedAt
      where device.id = :pushDeviceId
        and device.apnsToken = :expectedToken
      """)
  int deactivateIfTokenMatches(
      @Param("pushDeviceId") Long pushDeviceId,
      @Param("expectedToken") String expectedToken,
      @Param("deactivatedAt") java.time.Instant deactivatedAt);
}
