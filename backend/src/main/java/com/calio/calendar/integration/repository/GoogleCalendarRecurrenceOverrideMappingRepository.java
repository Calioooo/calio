package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarRecurrenceOverrideMappingRepository
        extends JpaRepository<GoogleCalendarRecurrenceOverrideMapping, Long> {

    Optional<GoogleCalendarRecurrenceOverrideMapping>
    findByRecurrenceEventOverride_OverrideId(Long recurrenceEventOverrideId);

    Optional<GoogleCalendarRecurrenceOverrideMapping>
    findByRecurrenceEventMapping_IdAndExternalEventId(
            Long recurrenceEventMappingId,
            String externalEventId
    );

    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join fetch overrideMapping.recurrenceEventMapping parentMapping
            where parentMapping.integration.id = :integrationId
              and parentMapping.calendarKey = :calendarKey
              and overrideMapping.externalEventId = :externalEventId
            """)
    List<GoogleCalendarRecurrenceOverrideMapping> findAllByExternalIdentity(
            @Param("integrationId") Long integrationId,
            @Param("calendarKey") String calendarKey,
            @Param("externalEventId") String externalEventId
    );

    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join fetch overrideMapping.recurrenceEventMapping parentMapping
            join fetch overrideMapping.recurrenceEventOverride
            where parentMapping.id in :parentMappingIds
              and overrideMapping.externalEventId in :externalEventIds
            """)
    List<GoogleCalendarRecurrenceOverrideMapping> findAllByParentAndExternalIdentity(
            @Param("parentMappingIds") Collection<Long> parentMappingIds,
            @Param("externalEventIds") Collection<String> externalEventIds
    );

    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join fetch overrideMapping.recurrenceEventMapping parentMapping
            join fetch overrideMapping.recurrenceEventOverride
            where parentMapping.id in :parentMappingIds
            """)
    List<GoogleCalendarRecurrenceOverrideMapping> findAllByParentMappingIds(
            @Param("parentMappingIds") Collection<Long> parentMappingIds
    );

    @Query("""
            select overrideMapping
            from GoogleCalendarRecurrenceOverrideMapping overrideMapping
            join fetch overrideMapping.recurrenceEventMapping parentMapping
            join fetch overrideMapping.recurrenceEventOverride
            where parentMapping.integration.id = :integrationId
            """)
    List<GoogleCalendarRecurrenceOverrideMapping> findAllByIntegrationId(
            @Param("integrationId") Long integrationId
    );
}
