package com.calio.calendar.integration.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.external.google.dto.GoogleCalendarEventItem;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarEventPagePersistenceService {

    private static final String UNTITLED_EVENT_TITLE = "(제목 없음)";

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleCalendarEventMappingRepository mappingRepository;
    private final EventRepository eventRepository;
    private final AccountRepository accountRepository;
    private final TagService tagService;
    private final GoogleCalendarEventTimeNormalizer timeNormalizer;

    public GoogleCalendarEventPagePersistenceService(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarEventMappingRepository mappingRepository,
            EventRepository eventRepository,
            AccountRepository accountRepository,
            TagService tagService,
            GoogleCalendarEventTimeNormalizer timeNormalizer
    ) {
        this.integrationRepository = integrationRepository;
        this.mappingRepository = mappingRepository;
        this.eventRepository = eventRepository;
        this.accountRepository = accountRepository;
        this.tagService = tagService;
        this.timeNormalizer = timeNormalizer;
    }

    @Transactional
    public void persistPage(
            Long integrationId,
            Long accountId,
            String runId,
            GoogleCalendarEventPage page
    ) {
        extendLeaseOrThrow(integrationId, runId);
        GoogleCalendarIntegration integration = integrationRepository.getReferenceById(integrationId);
        persistItems(integration, accountId, page.items(), page.timeZone());
    }

    @Transactional
    public void persistLastPageAndFinalize(
            Long integrationId,
            Long accountId,
            String runId,
            GoogleCalendarEventPage page
    ) {
        extendLeaseOrThrow(integrationId, runId);
        if (page.nextSyncToken() == null || page.nextSyncToken().isBlank()) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_TOKEN_MISSING);
        }
        GoogleCalendarIntegration integration = integrationRepository.getReferenceById(integrationId);
        persistItems(integration, accountId, page.items(), page.timeZone());
        if (integrationRepository.finalizeSync(integrationId, runId, page.nextSyncToken()) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
    }

    private void extendLeaseOrThrow(Long integrationId, String runId) {
        if (integrationRepository.extendSyncLease(integrationId, runId) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
    }

    private void persistItems(
            GoogleCalendarIntegration integration,
            Long accountId,
            List<GoogleCalendarEventItem> items,
            String pageTimeZone
    ) {
        Map<String, GoogleCalendarEventMapping> existingMappings =
                findExistingMappings(integration.getId(), items);
        boolean requiresNewEvent = items.stream()
                .anyMatch(item -> requiresNewEvent(item, existingMappings));
        Account account = requiresNewEvent ? accountRepository.getReferenceById(accountId) : null;
        Tag fallbackTag = requiresNewEvent ? tagService.getTagOrDefault(accountId, null) : null;
        List<GoogleCalendarEventMapping> mappingsToDelete = new ArrayList<>();
        List<Event> eventsToDelete = new ArrayList<>();

        for (GoogleCalendarEventItem item : items) {
            applyItem(
                    integration,
                    item,
                    existingMappings.get(item.id()),
                    account,
                    fallbackTag,
                    mappingsToDelete,
                    eventsToDelete,
                    pageTimeZone
            );
        }
        deleteMappingsAndEvents(mappingsToDelete, eventsToDelete);
    }

    private Map<String, GoogleCalendarEventMapping> findExistingMappings(
            Long integrationId,
            List<GoogleCalendarEventItem> items
    ) {
        Set<String> externalEventIds = new HashSet<>();
        for (GoogleCalendarEventItem item : items) {
            if (!externalEventIds.add(item.id())) {
                throw invalidResponse(null);
            }
        }
        if (externalEventIds.isEmpty()) {
            return Map.of();
        }
        return mappingRepository
                .findAllByExternalIdentity(
                        integrationId,
                        GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                        externalEventIds
                )
                .stream()
                .collect(Collectors.toMap(
                        GoogleCalendarEventMapping::getExternalEventId,
                        Function.identity()
                ));
    }

    private boolean requiresNewEvent(
            GoogleCalendarEventItem item,
            Map<String, GoogleCalendarEventMapping> existingMappings
    ) {
        return !item.isCancelled()
                && !item.isRecurring()
                && !existingMappings.containsKey(item.id());
    }

    private void applyItem(
            GoogleCalendarIntegration integration,
            GoogleCalendarEventItem item,
            GoogleCalendarEventMapping existingMapping,
            Account account,
            Tag fallbackTag,
            List<GoogleCalendarEventMapping> mappingsToDelete,
            List<Event> eventsToDelete,
            String pageTimeZone
    ) {
        if (item.isCancelled() || item.isRecurring()) {
            if (existingMapping != null) {
                mappingsToDelete.add(existingMapping);
                eventsToDelete.add(existingMapping.getEvent());
            }
            return;
        }
        NormalizedEventSchedule schedule =
                timeNormalizer.normalizeSchedule(item.start(), item.end(), pageTimeZone);
        if (existingMapping != null) {
            updateMappedEvent(existingMapping, item, schedule);
            return;
        }
        createMappedEvent(integration, account, fallbackTag, item, schedule);
    }

    private void updateMappedEvent(
            GoogleCalendarEventMapping mapping,
            GoogleCalendarEventItem item,
            NormalizedEventSchedule schedule
    ) {
        mapping.getEvent().replace(
                canonicalTitle(item.summary()),
                item.description(),
                schedule.startAt(),
                schedule.endAt(),
                schedule.allDay(),
                schedule.timeZone()
        );
        mapping.updateProviderVersion(item.etag(), item.updatedAt());
    }

    private void createMappedEvent(
            GoogleCalendarIntegration integration,
            Account account,
            Tag fallbackTag,
            GoogleCalendarEventItem item,
            NormalizedEventSchedule schedule
    ) {
        Event event = eventRepository.save(new Event(
                canonicalTitle(item.summary()),
                item.description(),
                schedule.startAt(),
                schedule.endAt(),
                schedule.allDay(),
                schedule.timeZone(),
                null,
                fallbackTag,
                account
        ));
        mappingRepository.save(new GoogleCalendarEventMapping(
                integration,
                event,
                item.id(),
                item.etag(),
                item.updatedAt()
        ));
    }

    private void deleteMappingsAndEvents(
            List<GoogleCalendarEventMapping> mappings,
            List<Event> events
    ) {
        if (mappings.isEmpty()) {
            return;
        }
        mappingRepository.deleteAll(mappings);
        mappingRepository.flush();
        eventRepository.deleteAll(events);
    }

    private String canonicalTitle(String summary) {
        return summary == null || summary.isBlank() ? UNTITLED_EVENT_TITLE : summary;
    }

    private CalioException invalidResponse(Exception cause) {
        return cause == null
                ? new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID)
                : new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID, cause);
    }

}
