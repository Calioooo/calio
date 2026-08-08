package com.calio.calendar.event.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.CreateEventRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.controller.dto.UpdateImportantEventRequest;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventApplicationService {

    private final EventQueryService eventQueryService;
    private final EventCommandService eventCommandService;
    private final AccountRepository accountRepository;
    private final TagService tagService;

    public EventApplicationService(
            EventQueryService eventQueryService,
            EventCommandService eventCommandService,
            AccountRepository accountRepository,
            TagService tagService
    ) {
        this.eventQueryService = eventQueryService;
        this.eventCommandService = eventCommandService;
        this.accountRepository = accountRepository;
        this.tagService = tagService;
    }

    @Transactional
    public EventResponse createEvent(Long accountId, CreateEventRequest request) {
        CanonicalSchedule.event(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
        Account account = accountRepository.getReferenceById(accountId);
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
        Event event = eventCommandService.createEvent(request.toEntity(tag, account));
        return EventResponse.from(event);
    }

    public EventResponse getEvent(Long accountId, Long eventId) {
        return EventResponse.from(eventQueryService.findEvent(accountId, eventId));
    }

    @Transactional
    public EventResponse updateEvent(Long accountId, Long eventId, UpdateEventRequest request) {
        Event event = eventQueryService.findEvent(accountId, eventId);
        rejectExternalEventMutation(accountId, eventId);
        CanonicalSchedule schedule = CanonicalSchedule.event(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
        eventCommandService.updateEvent(event, request, schedule, tag);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse updateImportantEvent(Long accountId, Long eventId, UpdateImportantEventRequest request) {
        Event event = eventQueryService.findEvent(accountId, eventId);
        eventCommandService.updateImportantEvent(event, request.importantEvent());
        return EventResponse.from(event);
    }

    @Transactional
    public void deleteEvent(Long accountId, Long eventId) {
        Event event = eventQueryService.findEvent(accountId, eventId);
        rejectExternalEventMutation(accountId, eventId);
        eventCommandService.deleteEvent(event);
    }

    public List<EventResponse> listEvents(Long accountId, Instant from, Instant to) {
        return eventQueryService.listEvents(accountId, from, to);
    }

    private void rejectExternalEventMutation(Long accountId, Long eventId) {
        if (eventQueryService.hasExternalEventMapping(accountId, eventId)) {
            throw new CalioException(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED);
        }
    }
}
