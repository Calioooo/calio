package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CreateEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.controller.dto.UpdateImportantEventRequest;
import com.calio.calendar.controller.dto.UpdateEventRequest;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.AccountRepository;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.Event;
import com.calio.calendar.repository.entity.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final AccountRepository accountRepository;
    private final TagService tagService;

    public EventService(EventRepository eventRepository, AccountRepository accountRepository, TagService tagService) {
        this.eventRepository = eventRepository;
        this.accountRepository = accountRepository;
        this.tagService = tagService;
    }

    @Transactional
    public EventResponse createEvent(Long accountId, CreateEventRequest request) {
        validateEventTimeRange(request.startAt(), request.endAt());
        Account account = accountRepository.getReferenceById(accountId);
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
        Event event = eventRepository.save(request.toEntity(tag, account));
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long accountId, Long eventId) {
        Event event = findEvent(accountId, eventId);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse updateEvent(Long accountId, Long eventId, UpdateEventRequest request) {
        validateEventTimeRange(request.startAt(), request.endAt());
        Event event = findEvent(accountId, eventId);
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
        event.replace(request.title(), request.description(), request.startAt(), request.endAt(), tag);
        eventRepository.flush();
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse updateImportantEvent(Long accountId, Long eventId, UpdateImportantEventRequest request) {
        Event event = findEvent(accountId, eventId);
        event.changeImportantEvent(request.importantEvent());
        eventRepository.flush();
        return EventResponse.from(event);
    }

    @Transactional
    public void deleteEvent(Long accountId, Long eventId) {
        Event event = findEvent(accountId, eventId);
        eventRepository.delete(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(Long accountId, Instant from, Instant to) {
        validateListTimeRange(from, to);
        return eventRepository.findByStartAtBetweenAndAccount_IdAndDeletedAtIsNullOrderByStartAtAsc(
                        from,
                        to,
                        accountId
                )
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    private Event findEvent(Long accountId, Long eventId) {
        return eventRepository.findByIdAndAccount_IdAndDeletedAtIsNull(eventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    }

    private void validateEventTimeRange(Instant startAt, Instant endAt) {
        if (startAt.isBefore(endAt)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }

    private void validateListTimeRange(Instant from, Instant to) {
        if (!from.isAfter(to)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }
}
