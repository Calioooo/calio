package com.calio.calendar.integration.connection.repository;

import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarConnectionRepository extends JpaRepository<GoogleCalendarConnection, Long> {

    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            join connection.integration integration
            where integration.accountId = :accountId
              and connection.state = com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState.CONNECTED
            """)
    Optional<GoogleCalendarConnection> findConnectedByAccountId(@Param("accountId") Long accountId);

    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            where connection.id = :connectionId
            """)
    Optional<GoogleCalendarConnection> findByIdWithIntegration(@Param("connectionId") Long connectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            join connection.integration integration
            where integration.accountId = :accountId
            """)
    Optional<GoogleCalendarConnection> findSingleConnectionByAccountIdForUpdate(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            join connection.integration integration
            where integration.accountId = :accountId
              and connection.state = com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState.CONNECTED
            """)
    Optional<GoogleCalendarConnection> findConnectedByAccountIdForUpdate(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            where connection.id = :connectionId
            """)
    Optional<GoogleCalendarConnection> findByIdForUpdate(@Param("connectionId") Long connectionId);

    @Query("""
            select connection.integration.accountId
            from GoogleCalendarConnection connection
            where connection.integration.accountId > :lastAccountId
              and connection.state = com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState.CONNECTED
            order by connection.integration.accountId
            """)
    List<Long> findConnectedAccountIdsAfter(@Param("lastAccountId") Long lastAccountId, Pageable pageable);

}
