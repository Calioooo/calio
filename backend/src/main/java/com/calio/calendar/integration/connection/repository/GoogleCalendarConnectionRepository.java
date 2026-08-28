package com.calio.calendar.integration.connection.repository;

import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState;
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
              and connection.state = :state
            """)
    Optional<GoogleCalendarConnection> findWithIntegrationByAccountIdAndState(
            @Param("accountId") Long accountId,
            @Param("state") GoogleCalendarConnectionState state
    );

    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            where connection.id = :connectionId
            """)
    Optional<GoogleCalendarConnection> findWithIntegrationById(@Param("connectionId") Long connectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            join connection.integration integration
            where integration.accountId = :accountId
              and connection.state = :state
            """)
    Optional<GoogleCalendarConnection> findWithIntegrationByAccountIdAndStateForUpdate(
            @Param("accountId") Long accountId,
            @Param("state") GoogleCalendarConnectionState state
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            where connection.integration.id = :integrationId
              and connection.googleSubject = :googleSubject
            """)
    Optional<GoogleCalendarConnection> findWithIntegrationByIntegrationIdAndGoogleSubjectForUpdate(
            @Param("integrationId") Long integrationId,
            @Param("googleSubject") String googleSubject
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            where connection.integration.id = :integrationId
              and connection.state = :state
            """)
    Optional<GoogleCalendarConnection> findWithIntegrationByIntegrationIdAndStateForUpdate(
            @Param("integrationId") Long integrationId,
            @Param("state") GoogleCalendarConnectionState state
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            where connection.id = :connectionId
            """)
    Optional<GoogleCalendarConnection> findWithIntegrationByIdForUpdate(@Param("connectionId") Long connectionId);

    @Query("""
            select connection.integration.accountId
            from GoogleCalendarConnection connection
            where connection.integration.accountId > :lastAccountId
              and connection.state = :state
            order by connection.integration.accountId
            """)
    List<Long> findAccountIdsByStateAfter(
            @Param("lastAccountId") Long lastAccountId,
            @Param("state") GoogleCalendarConnectionState state,
            Pageable pageable
    );

}
