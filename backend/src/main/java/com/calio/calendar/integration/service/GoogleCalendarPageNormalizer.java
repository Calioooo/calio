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
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.GeneralCancellation;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.GeneralUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.NormalizedItem;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceMasterCancellation;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceMasterUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.RecurrenceEventResult;
import com.calio.calendar.integration.service.GoogleCalendarSyncRunContext.MissingParent;
import com.calio.calendar.integration.service.GoogleCalendarSyncRunContext.ParentOutcome;
import com.calio.calendar.integration.service.GoogleCalendarSyncRunContext.ResolvedParent;
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

    private final GoogleCalendarEventMappingRepository eventMappingRepository;
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository;
    private final GoogleCalendarEventTimeNormalizer timeNormalizer;
    private final GoogleCalendarRecurrenceMapper recurrenceMapper;
    private final GoogleCalendarEventsClient eventsClient;
    private final GoogleCalendarAccessTokenService accessTokenService;

    public GoogleCalendarPageNormalizer(
            GoogleCalendarEventMappingRepository eventMappingRepository,
            GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository,
            GoogleCalendarEventTimeNormalizer timeNormalizer,
            GoogleCalendarRecurrenceMapper recurrenceMapper,
            GoogleCalendarEventsClient eventsClient,
            GoogleCalendarAccessTokenService accessTokenService
    ) {
        this.eventMappingRepository = eventMappingRepository;
        this.recurrenceMappingRepository = recurrenceMappingRepository;
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
        Set<String> itemIds = uniqueItemIds(page.items());
        Set<String> parentIds = parentIds(page.items());
        Set<String> masterLookupIds = new HashSet<>(itemIds);
        masterLookupIds.addAll(parentIds);

        Map<String, GoogleCalendarEventMapping> generalMappings =
                findGeneralMappings(integrationId, itemIds);
        Map<String, GoogleCalendarRecurrenceEventMapping> masterMappings =
                findMasterMappings(integrationId, masterLookupIds);
        Set<String> pageMasterIds = activePageMasterIds(page.items());

        LinkedHashMap<String, RecurrenceEventResult> fetchedParents = new LinkedHashMap<>();
        List<NormalizedItem> normalizedItems = new ArrayList<>();
        for (GoogleCalendarEventItem item : page.items()) {
            NormalizedItem normalized = normalizeItem(
                    integrationId,
                    item,
                    page.timeZone(),
                    generalMappings,
                    masterMappings,
                    pageMasterIds,
                    fetchedParents,
                    context
            );
            if (normalized != null) {
                normalizedItems.add(normalized);
                recordSeen(normalized, context);
            }
        }

        List<NormalizedItem> orderedItems = new ArrayList<>();
        fetchedParents.values().stream()
                .peek(result -> context.seeMaster(result.externalEventId()))
                .map(RecurrenceMasterUpsert::new)
                .forEach(orderedItems::add);
        normalizedItems.stream()
                .filter(RecurrenceMasterUpsert.class::isInstance)
                .forEach(orderedItems::add);
        normalizedItems.stream()
                .filter(item -> !(item instanceof RecurrenceMasterUpsert))
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
            Map<String, GoogleCalendarEventMapping> generalMappings,
            Map<String, GoogleCalendarRecurrenceEventMapping> masterMappings,
            Set<String> pageMasterIds,
            Map<String, RecurrenceEventResult> fetchedParents,
            GoogleCalendarSyncRunContext context
    ) {
        if (isCancelledException(item)) {
            if (!resolveParent(
                    integrationId,
                    item.recurringEventId(),
                    masterMappings,
                    pageMasterIds,
                    fetchedParents,
                    context
            )) {
                return null;
            }
            return new RecurrenceOverrideUpsert(recurrenceMapper.mapRecurrenceOverride(item));
        }
        if (item.isCancelled()) {
            if (masterMappings.containsKey(item.id())) {
                return new RecurrenceMasterCancellation(item.id());
            }
            if (generalMappings.containsKey(item.id())) {
                return new GeneralCancellation(item.id());
            }
            return null;
        }
        validateActiveShape(item);
        if (item.isRecurrenceEvent()) {
            return new RecurrenceMasterUpsert(recurrenceMapper.mapRecurrenceEvent(item));
        }
        if (item.isRecurrenceOverride()) {
            if (!resolveParent(
                    integrationId,
                    item.recurringEventId(),
                    masterMappings,
                    pageMasterIds,
                    fetchedParents,
                    context
            )) {
                return null;
            }
            return new RecurrenceOverrideUpsert(recurrenceMapper.mapRecurrenceOverride(item));
        }
        var schedule = timeNormalizer.normalizeSchedule(
                item.start(),
                item.end(),
                pageTimeZone
        );
        return new GeneralUpsert(
                item.id(),
                item.etag(),
                item.updatedAt(),
                canonicalTitle(item.summary()),
                item.description(),
                schedule
        );
    }

    private boolean resolveParent(
            Long integrationId,
            String externalMasterId,
            Map<String, GoogleCalendarRecurrenceEventMapping> masterMappings,
            Set<String> pageMasterIds,
            Map<String, RecurrenceEventResult> fetchedParents,
            GoogleCalendarSyncRunContext context
    ) {
        if (masterMappings.containsKey(externalMasterId)
                || pageMasterIds.contains(externalMasterId)) {
            return true;
        }
        ParentOutcome cached = context.parentOutcome(externalMasterId);
        if (cached instanceof ResolvedParent resolvedParent) {
            fetchedParents.putIfAbsent(externalMasterId, resolvedParent.result());
            return true;
        }
        if (cached == MissingParent.INSTANCE) {
            return false;
        }
        Optional<GoogleCalendarEventItem> response = requestParent(
                integrationId,
                externalMasterId,
                context
        );
        if (response.isEmpty()) {
            context.rememberParent(externalMasterId, MissingParent.INSTANCE);
            return false;
        }
        GoogleCalendarEventItem parent = response.get();
        validateParent(externalMasterId, parent);
        RecurrenceEventResult result = recurrenceMapper.mapRecurrenceEvent(parent);
        context.rememberParent(externalMasterId, new ResolvedParent(result));
        fetchedParents.put(externalMasterId, result);
        return true;
    }

    private Optional<GoogleCalendarEventItem> requestParent(
            Long integrationId,
            String externalMasterId,
            GoogleCalendarSyncRunContext context
    ) {
        try {
            return eventsClient.getEvent(context.accessToken(), externalMasterId);
        } catch (GoogleCalendarUnauthorizedException exception) {
            String refreshedToken = accessTokenService.forceRefresh(integrationId);
            context.replaceAccessToken(refreshedToken);
            try {
                return eventsClient.getEvent(refreshedToken, externalMasterId);
            } catch (GoogleCalendarUnauthorizedException retryException) {
                throw new CalioException(
                        ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED,
                        retryException
                );
            }
        }
    }

    private void validateParent(String requestedId, GoogleCalendarEventItem parent) {
        boolean isInvalid = !requestedId.equals(parent.id())
                || parent.isCancelled()
                || !parent.isRecurrenceEvent()
                || parent.isRecurrenceOverride();
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

    private boolean isCancelledException(GoogleCalendarEventItem item) {
        return item.isCancelled()
                && hasText(item.recurringEventId())
                && item.originalStartTime() != null;
    }

    private Set<String> uniqueItemIds(List<GoogleCalendarEventItem> items) {
        Set<String> itemIds = new HashSet<>();
        for (GoogleCalendarEventItem item : items) {
            if (!itemIds.add(item.id())) {
                throw invalidResponse();
            }
        }
        return itemIds;
    }

    private Set<String> parentIds(List<GoogleCalendarEventItem> items) {
        return items.stream()
                .map(GoogleCalendarEventItem::recurringEventId)
                .filter(this::hasText)
                .collect(Collectors.toSet());
    }

    private Set<String> activePageMasterIds(List<GoogleCalendarEventItem> items) {
        return items.stream()
                .filter(item -> !item.isCancelled())
                .filter(GoogleCalendarEventItem::isRecurrenceEvent)
                .filter(item -> !item.isRecurrenceOverride())
                .map(GoogleCalendarEventItem::id)
                .collect(Collectors.toSet());
    }

    private Map<String, GoogleCalendarEventMapping> findGeneralMappings(
            Long integrationId,
            Set<String> externalIds
    ) {
        if (externalIds.isEmpty()) {
            return Map.of();
        }
        return eventMappingRepository.findAllByExternalIdentity(
                        integrationId,
                        GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                        externalIds
                ).stream()
                .collect(Collectors.toMap(
                        GoogleCalendarEventMapping::getExternalEventId,
                        Function.identity()
                ));
    }

    private Map<String, GoogleCalendarRecurrenceEventMapping> findMasterMappings(
            Long integrationId,
            Set<String> externalIds
    ) {
        if (externalIds.isEmpty()) {
            return Map.of();
        }
        return recurrenceMappingRepository.findAllByExternalIdentity(
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
        if (item instanceof GeneralUpsert) {
            context.seeGeneral(item.externalEventId());
        } else if (item instanceof RecurrenceMasterUpsert) {
            context.seeMaster(item.externalEventId());
        } else if (item instanceof RecurrenceOverrideUpsert) {
            RecurrenceOverrideUpsert override = (RecurrenceOverrideUpsert) item;
            context.seeOverride(
                    override.result().parentExternalEventId(),
                    override.externalEventId()
            );
        }
    }

    private String canonicalTitle(String summary) {
        return summary == null || summary.isBlank() ? UNTITLED_EVENT_TITLE : summary;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private CalioException invalidResponse() {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
    }
}
