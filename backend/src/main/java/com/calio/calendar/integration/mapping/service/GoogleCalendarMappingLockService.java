package com.calio.calendar.integration.mapping.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarMappingSyncStatus;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.NormalizedItem;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acquires every existing mapping needed by an inbound page in one fixed order.
 *
 * <p>New mappings have no row to lock. Existing rows are always locked before their canonical
 * objects are read or changed: general mappings first, recurrence master mappings second, and
 * exact override mappings last.</p>
 */
@Service
@Transactional
public class GoogleCalendarMappingLockService {

    private final GoogleCalendarEventMappingRepository eventMappingRepository;
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceEventMappingRepository;
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository;

    public GoogleCalendarMappingLockService(
            GoogleCalendarEventMappingRepository eventMappingRepository,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceEventMappingRepository,
            GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository
    ) {
        this.eventMappingRepository = eventMappingRepository;
        this.recurrenceEventMappingRepository = recurrenceEventMappingRepository;
        this.overrideMappingRepository = overrideMappingRepository;
    }

    public LockedMappingIndex lockPageMappings(Long integrationId, List<NormalizedItem> items) {
        TreeSet<String> externalEventIds = items.stream()
                .map(NormalizedItem::externalEventId)
                .collect(Collectors.toCollection(TreeSet::new));
        TreeSet<String> recurrenceEventExternalIds = new TreeSet<>(externalEventIds);
        Map<String, String> expectedParents = expectedParentsByOverrideExternalId(items);
        recurrenceEventExternalIds.addAll(expectedParents.values());

        Map<String, GoogleCalendarEventMapping> events = indexEvents(
                externalEventIds.isEmpty() ? List.of()
                        : eventMappingRepository.findAllWithEventByExternalIdentityForUpdate(
                                integrationId,
                                GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                                externalEventIds
                        )
        );
        Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEvents = indexRecurrenceEvents(
                recurrenceEventExternalIds.isEmpty() ? List.of()
                        : recurrenceEventMappingRepository
                                .findAllWithRecurrenceEventAndTagByExternalIdentityForUpdate(
                                        integrationId,
                                        GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                                        recurrenceEventExternalIds
                                )
        );
        Map<OverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping> overrides =
                indexAndValidateOverrides(
                        expectedParents,
                        expectedParents.isEmpty() ? List.of() : overrideMappingRepository
                                .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByExternalEventIdsForUpdate(
                                        integrationId,
                                        GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                                        new TreeSet<>(expectedParents.keySet())
                                )
                );
        return new LockedMappingIndex(events, recurrenceEvents, overrides);
    }

    /** Locks an existing reconciliation batch in the same master-before-override order as pages. */
    public List<GoogleCalendarRecurrenceOverrideMapping> lockOverrideBatch(
            List<GoogleCalendarRecurrenceOverrideMapping> candidates
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        TreeSet<Long> recurrenceMappingIds = candidates.stream()
                .map(mapping -> mapping.getRecurrenceEventMapping().getId())
                .collect(Collectors.toCollection(TreeSet::new));
        recurrenceEventMappingRepository.findAllWithRecurrenceEventByIdsForUpdate(
                recurrenceMappingIds
        );
        TreeSet<Long> overrideMappingIds = candidates.stream()
                .map(GoogleCalendarRecurrenceOverrideMapping::getId)
                .collect(Collectors.toCollection(TreeSet::new));
        return overrideMappingRepository
                .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByIdsForUpdate(
                        overrideMappingIds
                );
    }

    public boolean isScopeConflictedAfterLocking(
            Long integrationId,
            GoogleCalendarEffectiveScope scope
    ) {
        return switch (scope) {
            case GoogleCalendarEffectiveScope.Event event -> eventMappingRepository
                    .findWithEventByScopeForUpdate(integrationId, event.eventId())
                    .map(mapping -> mapping.getSyncStatus() == GoogleCalendarMappingSyncStatus.CONFLICTED)
                    .orElse(false);
            case GoogleCalendarEffectiveScope.RecurrenceEvent recurrenceEvent ->
                    recurrenceEventMappingRepository
                            .findWithRecurrenceEventByScopeForUpdate(
                                    integrationId, recurrenceEvent.recurrenceEventId())
                            .map(mapping -> mapping.getSyncStatus()
                                    == GoogleCalendarMappingSyncStatus.CONFLICTED)
                            .orElse(false);
            case GoogleCalendarEffectiveScope.RecurrenceOverride override -> {
                boolean masterConflicted = recurrenceEventMappingRepository
                        .findWithRecurrenceEventByScopeForUpdate(
                                integrationId, override.recurrenceEventId())
                        .map(mapping -> mapping.getSyncStatus()
                                == GoogleCalendarMappingSyncStatus.CONFLICTED)
                        .orElse(false);
                if (masterConflicted) {
                    yield true;
                }
                yield overrideMappingRepository.findByScopeForUpdate(
                                integrationId, override.recurrenceEventId(), override.originStartAt())
                        .map(mapping -> mapping.getSyncStatus()
                                == GoogleCalendarMappingSyncStatus.CONFLICTED)
                        .orElse(false);
            }
        };
    }

    private Map<String, String> expectedParentsByOverrideExternalId(List<NormalizedItem> items) {
        Map<String, String> result = new HashMap<>();
        items.stream()
                .filter(RecurrenceEventOverrideUpsert.class::isInstance)
                .map(RecurrenceEventOverrideUpsert.class::cast)
                .forEach(item -> {
                    String previous = result.putIfAbsent(
                            item.externalEventId(), item.recurrenceEventExternalId());
                    if (previous != null && !previous.equals(item.recurrenceEventExternalId())) {
                        throw invalidResponse();
                    }
                });
        return result;
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

    private Map<OverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping>
    indexAndValidateOverrides(
            Map<String, String> expectedParents,
            Collection<GoogleCalendarRecurrenceOverrideMapping> mappings
    ) {
        Map<OverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping> result = new HashMap<>();
        for (GoogleCalendarRecurrenceOverrideMapping mapping : mappings) {
            String expectedParent = expectedParents.get(mapping.getExternalEventId());
            if (!mapping.getRecurrenceEventMapping().getExternalEventId().equals(expectedParent)) {
                throw invalidResponse();
            }
            result.put(new OverrideMappingKey(
                    mapping.getRecurrenceEventMapping().getId(), mapping.getExternalEventId()), mapping);
        }
        return result;
    }

    private CalioException invalidResponse() {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
    }

    public record LockedMappingIndex(
            Map<String, GoogleCalendarEventMapping> eventMappings,
            Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
            Map<OverrideMappingKey, GoogleCalendarRecurrenceOverrideMapping> overrideMappings
    ) {
    }

    public record OverrideMappingKey(Long recurrenceEventMappingId, String externalEventId) {
    }
}
