package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarMappingSyncStatus;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.NormalizedItem;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarMappingLockCoordinator {

    private final GoogleCalendarEventMappingRepository eventMappings;
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceEventMappings;
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappings;

    public GoogleCalendarMappingLockCoordinator(
            GoogleCalendarEventMappingRepository eventMappings,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceEventMappings,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappings
    ) {
        this.eventMappings = eventMappings;
        this.recurrenceEventMappings = recurrenceEventMappings;
        this.overrideMappings = overrideMappings;
    }

    public LockedMappings lockPage(Long integrationId, List<NormalizedItem> items) {
        Set<String> externalEventIds = items.stream()
                .map(NormalizedItem::externalEventId)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> recurrenceEventExternalIds = new TreeSet<>(externalEventIds);
        items.stream()
                .filter(RecurrenceEventOverrideUpsert.class::isInstance)
                .map(RecurrenceEventOverrideUpsert.class::cast)
                .map(RecurrenceEventOverrideUpsert::recurrenceEventExternalId)
                .forEach(recurrenceEventExternalIds::add);

        Map<String, GoogleCalendarEventMapping> lockedEvents = externalEventIds.isEmpty()
                ? new HashMap<>()
                : indexEvents(eventMappings.findAllWithEventByExternalIdentityForUpdate(
                        integrationId,
                        GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                        externalEventIds
                ));
        Map<String, GoogleCalendarRecurrenceEventMapping> lockedRecurrenceEvents =
                recurrenceEventExternalIds.isEmpty()
                        ? new HashMap<>()
                        : indexRecurrenceEvents(recurrenceEventMappings
                        .findAllWithRecurrenceEventAndTagByExternalIdentityForUpdate(
                                integrationId,
                                GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                                recurrenceEventExternalIds
                        ));

        Map<String, String> expectedMastersByOverride = expectedMastersByOverride(items);
        Map<OverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping> lockedOverrides =
                expectedMastersByOverride.isEmpty()
                        ? new HashMap<>()
                        : indexAndValidateOverrides(
                                integrationId,
                                expectedMastersByOverride,
                                overrideMappings
                                        .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByExternalEventIdsForUpdate(
                                                integrationId,
                                                GoogleCalendarRecurrenceEventMapping
                                                        .PRIMARY_CALENDAR_KEY,
                                                new TreeSet<>(expectedMastersByOverride.keySet())
                                        )
                        );
        return new LockedMappings(lockedEvents, lockedRecurrenceEvents, lockedOverrides);
    }

    public boolean isConflictedAfterLock(
            Long integrationId,
            GoogleCalendarEffectiveScope scope
    ) {
        return switch (scope) {
            case GoogleCalendarEffectiveScope.GeneralEvent event -> eventMappings
                    .findWithEventByIntegrationIdAndEventIdForUpdate(
                            integrationId, event.eventId())
                    .map(this::isConflicted)
                    .orElse(false);
            case GoogleCalendarEffectiveScope.RecurrenceMaster recurrenceEvent ->
                    recurrenceEventMappings
                            .findWithRecurrenceEventByIntegrationIdAndRecurrenceEventIdForUpdate(
                                    integrationId, recurrenceEvent.recurrenceEventId())
                            .map(this::isConflicted)
                            .orElse(false);
            case GoogleCalendarEffectiveScope.RecurrenceOverride override -> {
                boolean masterConflicted = recurrenceEventMappings
                        .findWithRecurrenceEventByIntegrationIdAndRecurrenceEventIdForUpdate(
                                integrationId, override.recurrenceEventId())
                        .map(this::isConflicted)
                        .orElse(false);
                if (masterConflicted) {
                    yield true;
                }
                yield overrideMappings
                        .findWithRecurrenceEventMappingAndRecurrenceEventOverrideByScopeForUpdate(
                                integrationId,
                                override.recurrenceEventId(),
                                override.originStartAt()
                        )
                        .map(this::isConflicted)
                        .orElse(false);
            }
        };
    }

    private Map<String, GoogleCalendarEventMapping> indexEvents(
            Collection<GoogleCalendarEventMapping> mappings
    ) {
        return mappings.stream().collect(Collectors.toMap(
                GoogleCalendarEventMapping::getExternalEventId,
                Function.identity(),
                (left, right) -> left,
                HashMap::new
        ));
    }

    private Map<String, GoogleCalendarRecurrenceEventMapping> indexRecurrenceEvents(
            Collection<GoogleCalendarRecurrenceEventMapping> mappings
    ) {
        return mappings.stream().collect(Collectors.toMap(
                GoogleCalendarRecurrenceEventMapping::getExternalEventId,
                Function.identity(),
                (left, right) -> left,
                HashMap::new
        ));
    }

    private Map<String, String> expectedMastersByOverride(List<NormalizedItem> items) {
        Map<String, String> expectedMasters = new HashMap<>();
        items.stream()
                .filter(RecurrenceEventOverrideUpsert.class::isInstance)
                .map(RecurrenceEventOverrideUpsert.class::cast)
                .forEach(override -> {
                    String previous = expectedMasters.putIfAbsent(
                            override.externalEventId(),
                            override.recurrenceEventExternalId()
                    );
                    if (previous != null
                            && !previous.equals(override.recurrenceEventExternalId())) {
                        throw invalidResponse();
                    }
                });
        return expectedMasters;
    }

    private Map<OverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping>
            indexAndValidateOverrides(
                    Long integrationId,
                    Map<String, String> expectedMasters,
                    Collection<GoogleCalendarRecurrenceOverrideMapping> mappings
            ) {
        Map<OverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping> indexed =
                new HashMap<>();
        for (GoogleCalendarRecurrenceOverrideMapping mapping : mappings) {
            if (!mapping.getRecurrenceEventMapping().getIntegration().getId()
                    .equals(integrationId)) {
                throw invalidResponse();
            }
            String expectedMaster = expectedMasters.get(mapping.getExternalEventId());
            if (!mapping.getRecurrenceEventMapping().getExternalEventId()
                    .equals(expectedMaster)) {
                throw invalidResponse();
            }
            indexed.put(
                    new OverrideMappingKey(
                            mapping.getRecurrenceEventMapping().getId(),
                            mapping.getExternalEventId()
                    ),
                    mapping
            );
        }
        return indexed;
    }

    private boolean isConflicted(GoogleCalendarEventMapping mapping) {
        return mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED;
    }

    private boolean isConflicted(GoogleCalendarRecurrenceEventMapping mapping) {
        return mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED;
    }

    private boolean isConflicted(GoogleCalendarRecurrenceOverrideMapping mapping) {
        return mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED;
    }

    private CalioException invalidResponse() {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
    }

    public record LockedMappings(
            Map<String, GoogleCalendarEventMapping> eventMappings,
            Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
            Map<OverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping> overrideMappings
    ) {
    }

    public record OverrideMappingKey(
            Long recurrenceEventMappingId,
            String overrideExternalEventId
    ) {
    }
}
