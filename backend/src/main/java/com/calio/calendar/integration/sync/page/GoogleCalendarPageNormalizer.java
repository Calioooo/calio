package com.calio.calendar.integration.sync.page;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.integration.sync.GoogleCalendarSyncRunContext;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventCancellation;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.NormalizedItem;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventCancellation;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarPageNormalizer {

    private static final String UNTITLED_EVENT_TITLE = "(제목 없음)";

    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService;
    private final GoogleCalendarEventTimeNormalizer timeNormalizer;
    private final GoogleCalendarRecurrenceMapper recurrenceMapper;
    private final GoogleCalendarRecurrenceEventLoader recurrenceEventLoader;

    public GoogleCalendarPageNormalizer(
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService,
            GoogleCalendarEventTimeNormalizer timeNormalizer,
            GoogleCalendarRecurrenceMapper recurrenceMapper,
            GoogleCalendarRecurrenceEventLoader recurrenceEventLoader
    ) {
        this.eventMappingQueryService = eventMappingQueryService;
        this.recurrenceMappingQueryService = recurrenceMappingQueryService;
        this.timeNormalizer = timeNormalizer;
        this.recurrenceMapper = recurrenceMapper;
        this.recurrenceEventLoader = recurrenceEventLoader;
    }

    public GoogleCalendarNormalizedPage normalize(
            Long integrationId,
            GoogleCalendarEventPage page,
            GoogleCalendarSyncRunContext context
    ) {
        Set<String> externalEventIds = uniqueExternalEventIds(page.items());
        Set<String> recurrenceEventReferenceIds = recurrenceEventReferenceIds(page.items());
        Set<String> recurrenceEventLookupIds = new HashSet<>(externalEventIds);
        recurrenceEventLookupIds.addAll(recurrenceEventReferenceIds);

        Map<String, GoogleCalendarEventMapping> eventMappings =
                findEventMappings(integrationId, externalEventIds);
        Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings =
                findRecurrenceEventMappings(integrationId, recurrenceEventLookupIds);
        Set<String> pageRecurrenceEventIds = activePageRecurrenceEventIds(page.items());

        LinkedHashMap<String, RecurrenceEventUpsert> fetchedRecurrenceEvents =
                new LinkedHashMap<>();
        List<NormalizedItem> normalizedItems = new ArrayList<>();
        for (GoogleCalendarEventResponse item : page.items()) {
            NormalizedItem normalized = normalizeItem(
                    integrationId,
                    item,
                    page.timeZone(),
                    eventMappings,
                    recurrenceEventMappings,
                    pageRecurrenceEventIds,
                    fetchedRecurrenceEvents,
                    context
            );
            if (normalized != null) {
                normalizedItems.add(normalized);
                recordSeen(normalized, context);
            }
        }

        List<NormalizedItem> orderedItems = orderForPersistence(
                fetchedRecurrenceEvents,
                normalizedItems,
                context
        );
        return new GoogleCalendarNormalizedPage(
                orderedItems,
                page.nextPageToken(),
                page.nextSyncToken()
        );
    }

    private List<NormalizedItem> orderForPersistence(
            Map<String, RecurrenceEventUpsert> fetchedRecurrenceEvents,
            List<NormalizedItem> normalizedItems,
            GoogleCalendarSyncRunContext context
    ) {
        List<NormalizedItem> orderedItems = new ArrayList<>();

        // Override는 부모 recurrence-event mapping을 참조하고, series cancellation은 그 mapping을 제거한다.
        // 따라서 parent upsert를 먼저 반영하고 series cancellation을 마지막에 반영한다.
        for (RecurrenceEventUpsert recurrenceEvent : fetchedRecurrenceEvents.values()) {
            context.seeRecurrenceEvent(recurrenceEvent.externalEventId());
            orderedItems.add(recurrenceEvent);
        }
        appendRecurrenceEventUpserts(normalizedItems, orderedItems);
        appendEventAndOverrideItems(normalizedItems, orderedItems);
        appendRecurrenceEventCancellations(normalizedItems, orderedItems);

        return orderedItems;
    }

    private void appendRecurrenceEventUpserts(
            List<NormalizedItem> normalizedItems,
            List<NormalizedItem> orderedItems
    ) {
        normalizedItems.stream()
                .filter(RecurrenceEventUpsert.class::isInstance)
                .forEach(orderedItems::add);
    }

    private void appendEventAndOverrideItems(
            List<NormalizedItem> normalizedItems,
            List<NormalizedItem> orderedItems
    ) {
        normalizedItems.stream()
                .filter(item -> !(item instanceof RecurrenceEventUpsert))
                .filter(item -> !(item instanceof RecurrenceEventCancellation))
                .forEach(orderedItems::add);
    }

    private void appendRecurrenceEventCancellations(
            List<NormalizedItem> normalizedItems,
            List<NormalizedItem> orderedItems
    ) {
        normalizedItems.stream()
                .filter(RecurrenceEventCancellation.class::isInstance)
                .forEach(orderedItems::add);
    }

    private NormalizedItem normalizeItem(
            Long integrationId,
            GoogleCalendarEventResponse item,
            String pageTimeZone,
            Map<String, GoogleCalendarEventMapping> eventMappings,
            Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
            Set<String> pageRecurrenceEventIds,
            Map<String, RecurrenceEventUpsert> fetchedRecurrenceEvents,
            GoogleCalendarSyncRunContext context
    ) {
        if (item.isCancelled()) {
            return normalizeCancelledItem(
                    integrationId,
                    item,
                    eventMappings,
                    recurrenceEventMappings,
                    pageRecurrenceEventIds,
                    fetchedRecurrenceEvents,
                    context
            );
        }
        validateActiveShape(item);
        if (item.isRecurrenceEvent()) {
            return recurrenceMapper.mapRecurrenceEvent(item);
        }
        if (item.isRecurrenceOverride()) {
            return normalizeRecurrenceEventOverride(
                    integrationId,
                    item,
                    recurrenceEventMappings,
                    pageRecurrenceEventIds,
                    fetchedRecurrenceEvents,
                    context
            );
        }
        return normalizeEvent(item, pageTimeZone);
    }

    private NormalizedItem normalizeCancelledItem(
            Long integrationId,
            GoogleCalendarEventResponse item,
            Map<String, GoogleCalendarEventMapping> eventMappings,
            Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
            Set<String> pageRecurrenceEventIds,
            Map<String, RecurrenceEventUpsert> fetchedRecurrenceEvents,
            GoogleCalendarSyncRunContext context
    ) {
        if (isDeletedRecurrenceOccurrence(item)) {
            return normalizeRecurrenceEventOverride(
                    integrationId,
                    item,
                    recurrenceEventMappings,
                    pageRecurrenceEventIds,
                    fetchedRecurrenceEvents,
                    context
            );
        }
        if (recurrenceEventMappings.containsKey(item.id())) {
            return new RecurrenceEventCancellation(item.id());
        }
        if (eventMappings.containsKey(item.id())) {
            return new EventCancellation(item.id());
        }
        return null;
    }

    private RecurrenceEventOverrideUpsert normalizeRecurrenceEventOverride(
            Long integrationId,
            GoogleCalendarEventResponse item,
            Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
            Set<String> pageRecurrenceEventIds,
            Map<String, RecurrenceEventUpsert> fetchedRecurrenceEvents,
            GoogleCalendarSyncRunContext context
    ) {
        String recurrenceEventExternalId = item.recurringEventId();
        if (hasRecurrenceEvent(
                recurrenceEventExternalId,
                recurrenceEventMappings,
                pageRecurrenceEventIds
        )) {
            return recurrenceMapper.mapRecurrenceOverride(item);
        }

        Optional<RecurrenceEventUpsert> recurrenceEvent =
                recurrenceEventLoader.loadRecurrenceEvent(
                        integrationId,
                        recurrenceEventExternalId,
                        context
                );
        if (recurrenceEvent.isEmpty()) {
            return null;
        }
        fetchedRecurrenceEvents.putIfAbsent(
                recurrenceEventExternalId,
                recurrenceEvent.get()
        );
        return recurrenceMapper.mapRecurrenceOverride(item);
    }

    private EventUpsert normalizeEvent(
            GoogleCalendarEventResponse item,
            String pageTimeZone
    ) {
        var schedule = timeNormalizer.normalizeSchedule(
                item.start(),
                item.end(),
                pageTimeZone
        );
        return new EventUpsert(
                item.id(),
                item.etag(),
                item.updatedAt(),
                eventTitle(item.summary()),
                item.description(),
                schedule
        );
    }

    private boolean hasRecurrenceEvent(
            String recurrenceEventExternalId,
            Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
            Set<String> pageRecurrenceEventIds
    ) {
        return recurrenceEventMappings.containsKey(recurrenceEventExternalId)
                || pageRecurrenceEventIds.contains(recurrenceEventExternalId);
    }

    private void validateActiveShape(GoogleCalendarEventResponse item) {
        boolean isMixedRecurrenceShape = item.isRecurrenceEvent()
                && item.isRecurrenceOverride();
        boolean hasOrphanOrigin = item.originalStartTime() != null
                && !item.isRecurrenceOverride();
        if (isMixedRecurrenceShape || hasOrphanOrigin) {
            throw invalidResponse();
        }
    }

    private boolean isDeletedRecurrenceOccurrence(GoogleCalendarEventResponse item) {
        return item.isCancelled()
                && hasText(item.recurringEventId())
                && item.originalStartTime() != null;
    }

    private Set<String> uniqueExternalEventIds(List<GoogleCalendarEventResponse> items) {
        Set<String> externalEventIds = new HashSet<>();
        for (GoogleCalendarEventResponse item : items) {
            if (!externalEventIds.add(item.id())) {
                throw invalidResponse();
            }
        }
        return externalEventIds;
    }

    private Set<String> recurrenceEventReferenceIds(List<GoogleCalendarEventResponse> items) {
        return items.stream()
                .map(GoogleCalendarEventResponse::recurringEventId)
                .filter(this::hasText)
                .collect(Collectors.toSet());
    }

    private Set<String> activePageRecurrenceEventIds(List<GoogleCalendarEventResponse> items) {
        return items.stream()
                .filter(item -> !item.isCancelled())
                .filter(GoogleCalendarEventResponse::isRecurrenceEvent)
                .filter(item -> !item.isRecurrenceOverride())
                .map(GoogleCalendarEventResponse::id)
                .collect(Collectors.toSet());
    }

    private Map<String, GoogleCalendarEventMapping> findEventMappings(
            Long integrationId,
            Set<String> externalIds
    ) {
        if (externalIds.isEmpty()) {
            return Map.of();
        }
        return eventMappingQueryService.listEventMappings(
                        integrationId,
                        GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                        externalIds
                ).stream()
                .collect(Collectors.toMap(
                        GoogleCalendarEventMapping::getExternalEventId,
                        Function.identity()
                ));
    }

    private Map<String, GoogleCalendarRecurrenceEventMapping> findRecurrenceEventMappings(
            Long integrationId,
            Set<String> externalIds
    ) {
        if (externalIds.isEmpty()) {
            return Map.of();
        }
        return recurrenceMappingQueryService.listRecurrenceEventMappings(
                        integrationId,
                        GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                        externalIds
                ).stream()
                .collect(Collectors.toMap(
                        GoogleCalendarRecurrenceEventMapping::getExternalEventId,
                        Function.identity()
                ));
    }

    private void recordSeen(
            NormalizedItem item,
            GoogleCalendarSyncRunContext context
    ) {
        if (item instanceof EventUpsert) {
            context.seeEvent(item.externalEventId());
        } else if (item instanceof RecurrenceEventUpsert) {
            context.seeRecurrenceEvent(item.externalEventId());
        } else if (item instanceof RecurrenceEventOverrideUpsert override) {
            context.seeRecurrenceEventOverride(
                    override.recurrenceEventExternalId(),
                    override.externalEventId()
            );
        }
    }

    private String eventTitle(String summary) {
        return summary == null || summary.isBlank() ? UNTITLED_EVENT_TITLE : summary;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private CalioException invalidResponse() {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
    }
}
