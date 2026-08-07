package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer;
import com.calio.calendar.external.google.GoogleCalendarUnauthorizedException;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventItem;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.EventCancellation;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.NormalizedItem;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.RecurrenceEventCancellation;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarRecurrenceEventLookup;
import com.calio.calendar.integration.service.dto.GoogleCalendarRecurrenceEventLookup.FoundRecurrenceEvent;
import com.calio.calendar.integration.service.dto.GoogleCalendarRecurrenceEventLookup.MissingRecurrenceEvent;
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
    private final GoogleCalendarEventsClient eventsClient;
    private final GoogleCalendarAccessTokenService accessTokenService;

    public GoogleCalendarPageNormalizer(
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService,
            GoogleCalendarEventTimeNormalizer timeNormalizer,
            GoogleCalendarRecurrenceMapper recurrenceMapper,
            GoogleCalendarEventsClient eventsClient,
            GoogleCalendarAccessTokenService accessTokenService
    ) {
        this.eventMappingQueryService = eventMappingQueryService;
        this.recurrenceMappingQueryService = recurrenceMappingQueryService;
        this.timeNormalizer = timeNormalizer;
        this.recurrenceMapper = recurrenceMapper;
        this.eventsClient = eventsClient;
        this.accessTokenService = accessTokenService;
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
        for (GoogleCalendarEventItem item : page.items()) {
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

        List<NormalizedItem> orderedItems = new ArrayList<>();
        for (RecurrenceEventUpsert recurrenceEvent : fetchedRecurrenceEvents.values()) {
            context.seeRecurrenceEvent(recurrenceEvent.externalEventId());
            orderedItems.add(recurrenceEvent);
        }
        normalizedItems.stream()
                .filter(RecurrenceEventUpsert.class::isInstance)
                .forEach(orderedItems::add);
        normalizedItems.stream()
                .filter(item -> !(item instanceof RecurrenceEventUpsert))
                .filter(item -> !(item instanceof RecurrenceEventCancellation))
                .forEach(orderedItems::add);
        normalizedItems.stream()
                .filter(RecurrenceEventCancellation.class::isInstance)
                .forEach(orderedItems::add);
        return new GoogleCalendarNormalizedPage(
                orderedItems,
                page.nextPageToken(),
                page.nextSyncToken()
        );
    }

    private NormalizedItem normalizeItem(
            Long integrationId,
            GoogleCalendarEventItem item,
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
            GoogleCalendarEventItem item,
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
            GoogleCalendarEventItem item,
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

        Optional<RecurrenceEventUpsert> recurrenceEvent = loadRecurrenceEvent(
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
            GoogleCalendarEventItem item,
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

    private Optional<RecurrenceEventUpsert> loadRecurrenceEvent(
            Long integrationId,
            String recurrenceEventExternalId,
            GoogleCalendarSyncRunContext context
    ) {
        GoogleCalendarRecurrenceEventLookup cached =
                context.recurrenceEventLookup(recurrenceEventExternalId);
        if (cached instanceof FoundRecurrenceEvent found) {
            return Optional.of(found.recurrenceEvent());
        }
        if (cached == MissingRecurrenceEvent.INSTANCE) {
            return Optional.empty();
        }
        Optional<GoogleCalendarEventItem> response = requestRecurrenceEvent(
                integrationId,
                recurrenceEventExternalId,
                context
        );
        if (response.isEmpty()) {
            context.rememberRecurrenceEvent(
                    recurrenceEventExternalId,
                    MissingRecurrenceEvent.INSTANCE
            );
            return Optional.empty();
        }
        GoogleCalendarEventItem recurrenceEventResponse = response.get();
        validateRecurrenceEventResponse(recurrenceEventExternalId, recurrenceEventResponse);
        RecurrenceEventUpsert recurrenceEvent =
                recurrenceMapper.mapRecurrenceEvent(recurrenceEventResponse);
        context.rememberRecurrenceEvent(
                recurrenceEventExternalId,
                new FoundRecurrenceEvent(recurrenceEvent)
        );
        return Optional.of(recurrenceEvent);
    }

    private Optional<GoogleCalendarEventItem> requestRecurrenceEvent(
            Long integrationId,
            String recurrenceEventExternalId,
            GoogleCalendarSyncRunContext context
    ) {
        try {
            return eventsClient.getEvent(context.accessToken(), recurrenceEventExternalId);
        } catch (GoogleCalendarUnauthorizedException exception) {
            String refreshedToken = accessTokenService.forceRefresh(integrationId);
            context.replaceAccessToken(refreshedToken);
            try {
                return eventsClient.getEvent(refreshedToken, recurrenceEventExternalId);
            } catch (GoogleCalendarUnauthorizedException retryException) {
                throw new CalioException(
                        ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED,
                        retryException
                );
            }
        }
    }

    private void validateRecurrenceEventResponse(
            String requestedId,
            GoogleCalendarEventItem recurrenceEvent
    ) {
        boolean isInvalid = !requestedId.equals(recurrenceEvent.id())
                || recurrenceEvent.isCancelled()
                || !recurrenceEvent.isRecurrenceEvent()
                || recurrenceEvent.isRecurrenceOverride();
        if (isInvalid) {
            throw invalidResponse();
        }
    }

    private void validateActiveShape(GoogleCalendarEventItem item) {
        boolean isMixedRecurrenceShape = item.isRecurrenceEvent()
                && item.isRecurrenceOverride();
        boolean hasOrphanOrigin = item.originalStartTime() != null
                && !item.isRecurrenceOverride();
        if (isMixedRecurrenceShape || hasOrphanOrigin) {
            throw invalidResponse();
        }
    }

    private boolean isDeletedRecurrenceOccurrence(GoogleCalendarEventItem item) {
        return item.isCancelled()
                && hasText(item.recurringEventId())
                && item.originalStartTime() != null;
    }

    private Set<String> uniqueExternalEventIds(List<GoogleCalendarEventItem> items) {
        Set<String> externalEventIds = new HashSet<>();
        for (GoogleCalendarEventItem item : items) {
            if (!externalEventIds.add(item.id())) {
                throw invalidResponse();
            }
        }
        return externalEventIds;
    }

    private Set<String> recurrenceEventReferenceIds(List<GoogleCalendarEventItem> items) {
        return items.stream()
                .map(GoogleCalendarEventItem::recurringEventId)
                .filter(this::hasText)
                .collect(Collectors.toSet());
    }

    private Set<String> activePageRecurrenceEventIds(List<GoogleCalendarEventItem> items) {
        return items.stream()
                .filter(item -> !item.isCancelled())
                .filter(GoogleCalendarEventItem::isRecurrenceEvent)
                .filter(item -> !item.isRecurrenceOverride())
                .map(GoogleCalendarEventItem::id)
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
