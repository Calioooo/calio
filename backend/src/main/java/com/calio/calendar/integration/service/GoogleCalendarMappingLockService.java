package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleCalendarSyncTarget;
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
public class GoogleCalendarMappingLockService {

    private final GoogleCalendarEventMappingRepository eventMappingRepository;
    private final GoogleCalendarRecurrenceEventMappingRepository
            recurrenceEventMappingRepository;
    private final GoogleCalendarRecurrenceOverrideMappingRepository
            overrideMappingRepository;

    public GoogleCalendarMappingLockService(
            GoogleCalendarEventMappingRepository eventMappingRepository,
            GoogleCalendarRecurrenceEventMappingRepository
                    recurrenceEventMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository
    ) {
        this.eventMappingRepository = eventMappingRepository;
        this.recurrenceEventMappingRepository = recurrenceEventMappingRepository;
        this.overrideMappingRepository = overrideMappingRepository;
    }

    public LockedMappingIndex lockMappingsForPage(Long integrationId, List<NormalizedItem> items) {
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
                : indexEvents(eventMappingRepository
                        .findAllWithEventByExternalIdentityForUpdate(
                                integrationId,
                                GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                                externalEventIds
                        ));
        Map<String, GoogleCalendarRecurrenceEventMapping> lockedRecurrenceEvents =
                recurrenceEventExternalIds.isEmpty()
                        ? new HashMap<>()
                        : indexRecurrenceEvents(recurrenceEventMappingRepository
                                .findAllWithRecurrenceEventAndTagByExternalIdentityForUpdate(
                                        integrationId,
                                        GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                                        recurrenceEventExternalIds
                                ));

        Map<String, String> expectedRecurrenceEventIdsByOverrideId =
                findExpectedRecurrenceEventIdsByOverrideId(items);
        Map<RecurrenceOverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping> lockedOverrides =
                expectedRecurrenceEventIdsByOverrideId.isEmpty()
                        ? new HashMap<>()
                        : indexAndValidateOverrides(
                                integrationId,
                                expectedRecurrenceEventIdsByOverrideId,
                                overrideMappingRepository
                                        .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByExternalEventIdsForUpdate(
                                                integrationId,
                                                GoogleCalendarRecurrenceEventMapping
                                                        .PRIMARY_CALENDAR_KEY,
                                                new TreeSet<>(
                                                        expectedRecurrenceEventIdsByOverrideId
                                                                .keySet())
                                        )
                        );
        return new LockedMappingIndex(lockedEvents, lockedRecurrenceEvents, lockedOverrides);
    }

    public boolean isTargetConflictedAfterLocking(
            Long integrationId,
            GoogleCalendarSyncTarget syncTarget
    ) {
        return switch (syncTarget) {
            case GoogleCalendarSyncTarget.Event event -> eventMappingRepository
                    .findWithEventByIntegrationIdAndEventIdForUpdate(
                            integrationId, event.eventId())
                    .map(this::isConflicted)
                    .orElse(false);
            case GoogleCalendarSyncTarget.RecurrenceEvent recurrenceEvent ->
                    recurrenceEventMappingRepository
                            .findWithRecurrenceEventByIntegrationIdAndRecurrenceEventIdForUpdate(
                                    integrationId, recurrenceEvent.recurrenceEventId())
                            .map(this::isConflicted)
                            .orElse(false);
            case GoogleCalendarSyncTarget.RecurrenceOverride override -> {
                boolean recurrenceEventConflicted = recurrenceEventMappingRepository
                        .findWithRecurrenceEventByIntegrationIdAndRecurrenceEventIdForUpdate(
                                integrationId, override.recurrenceEventId())
                        .map(this::isConflicted)
                        .orElse(false);
                if (recurrenceEventConflicted) {
                    yield true;
                }
                yield overrideMappingRepository
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

    private Map<String, String> findExpectedRecurrenceEventIdsByOverrideId(
            List<NormalizedItem> items
    ) {
        Map<String, String> recurrenceEventIdsByOverrideId = new HashMap<>();
        items.stream()
                .filter(RecurrenceEventOverrideUpsert.class::isInstance)
                .map(RecurrenceEventOverrideUpsert.class::cast)
                .forEach(override -> {
                    String previousRecurrenceEventId = recurrenceEventIdsByOverrideId
                            .putIfAbsent(
                                    override.externalEventId(),
                                    override.recurrenceEventExternalId()
                            );
                    if (previousRecurrenceEventId != null
                            && !previousRecurrenceEventId.equals(
                                    override.recurrenceEventExternalId())) {
                        throw invalidResponse();
                    }
                });
        return recurrenceEventIdsByOverrideId;
    }

    private Map<RecurrenceOverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping>
            indexAndValidateOverrides(
                    Long integrationId,
                    Map<String, String> expectedRecurrenceEventIdsByOverrideId,
                    Collection<GoogleCalendarRecurrenceOverrideMapping> mappings
            ) {
        Map<RecurrenceOverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping> indexed =
                new HashMap<>();
        for (GoogleCalendarRecurrenceOverrideMapping mapping : mappings) {
            if (!mapping.getRecurrenceEventMapping().getIntegration().getId()
                    .equals(integrationId)) {
                throw invalidResponse();
            }
            String expectedRecurrenceEventId =
                    expectedRecurrenceEventIdsByOverrideId.get(mapping.getExternalEventId());
            if (!mapping.getRecurrenceEventMapping().getExternalEventId()
                    .equals(expectedRecurrenceEventId)) {
                throw invalidResponse();
            }
            indexed.put(
                    new RecurrenceOverrideMappingKey(
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

    public record LockedMappingIndex(
            Map<String, GoogleCalendarEventMapping> eventMappings,
            Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
            Map<RecurrenceOverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping> overrideMappings
    ) {
    }

    public record RecurrenceOverrideMappingKey(
            Long recurrenceEventMappingId,
            String overrideExternalEventId
    ) {
    }
}
