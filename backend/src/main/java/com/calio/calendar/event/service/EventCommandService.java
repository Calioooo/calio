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
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EventCommandService {

    private final EventRepository eventRepository;
    private final AccountRepository accountRepository;
    private final TagService tagService;
    private final GoogleCalendarEventMappingRepository googleCalendarEventMappingRepository;

    public EventCommandService(
            EventRepository eventRepository,
            AccountRepository accountRepository,
            TagService tagService,
            GoogleCalendarEventMappingRepository googleCalendarEventMappingRepository
    ) {
        this.eventRepository = eventRepository;
        this.accountRepository = accountRepository;
        this.tagService = tagService;
        this.googleCalendarEventMappingRepository = googleCalendarEventMappingRepository;
    }

    public EventResponse createEvent(Long accountId, CreateEventRequest request) {
        CanonicalSchedule.event(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
        Account account = accountRepository.getReferenceById(accountId);
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
        Event event = eventRepository.save(request.toEntity(tag, account));
        return EventResponse.from(event);
    }

    public EventResponse updateEvent(Long accountId, Long eventId, UpdateEventRequest request) {
        Event event = findEvent(accountId, eventId);
        rejectExternalEventMutation(accountId, eventId);
        CanonicalSchedule schedule = CanonicalSchedule.event(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
        event.replace(
                request.title(),
                request.description(),
                schedule.startAt(),
                schedule.endAt(),
                schedule.allDay(),
                schedule.timeZone(),
                tag
        );
        eventRepository.flush();
        return EventResponse.from(event);
    }

    public EventResponse updateImportantEvent(Long accountId, Long eventId, UpdateImportantEventRequest request) {
        Event event = findEvent(accountId, eventId);
        event.changeImportantEvent(request.importantEvent());
        eventRepository.flush();
        return EventResponse.from(event);
    }

    public void deleteEvent(Long accountId, Long eventId) {
        Event event = findEvent(accountId, eventId);
        rejectExternalEventMutation(accountId, eventId);
        eventRepository.delete(event);
    }

    private Event findEvent(Long accountId, Long eventId) {
        return eventRepository.findByIdAndAccount_Id(eventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    }

    private void rejectExternalEventMutation(Long accountId, Long eventId) {
        if (googleCalendarEventMappingRepository
                .existsByEvent_IdAndIntegration_AccountId(eventId, accountId)) {
            throw new CalioException(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED);
        }
    }
}
