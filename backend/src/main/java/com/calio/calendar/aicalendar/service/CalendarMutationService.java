package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.domain.CalendarMutationOperation;
import com.calio.calendar.aicalendar.domain.CalendarMutationScope;
import com.calio.calendar.aicalendar.domain.CalendarMutationType;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationPreview;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarMutationToolRequest;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.CreateEventRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.service.TagService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CalendarMutationService {

    private final EventService eventService;
    private final TagService tagService;

    public CalendarMutationService(EventService eventService, TagService tagService) {
        this.eventService = eventService;
        this.tagService = tagService;
    }

    public CalendarMutationPreview preview(Long accountId, CalendarMutationToolRequest request) {
        return switch (requireOperation(request)) {
            case CREATE_EVENT -> previewEventCreation(accountId, request);
            case UPDATE_EVENT -> previewEventUpdate(accountId, request);
            case DELETE_EVENT -> previewEventDeletion(accountId, request);
        };
    }

    public List<EventResponse> apply(Long accountId, CalendarMutationToolRequest request) {
        return switch (requireOperation(request)) {
            case CREATE_EVENT -> List.of(eventService.createEvent(accountId, createEventRequest(request)));
            case UPDATE_EVENT -> applyEventUpdate(accountId, request);
            case DELETE_EVENT -> deleteEvent(accountId, request);
        };
    }

    private CalendarMutationPreview previewEventCreation(Long accountId, CalendarMutationToolRequest request) {
        CreateEventRequest eventRequest = createEventRequest(request);
        return new CalendarMutationPreview(
                CalendarMutationType.CREATE,
                CalendarMutationScope.EVENT,
                null,
                eventForCreation(accountId, eventRequest)
        );
    }

    private CalendarMutationPreview previewEventUpdate(Long accountId, CalendarMutationToolRequest request) {
        EventUpdate update = prepareEventUpdate(accountId, request);
        return new CalendarMutationPreview(
                CalendarMutationType.UPDATE,
                CalendarMutationScope.EVENT,
                update.before(),
                eventForUpdate(accountId, request, update)
        );
    }

    private CalendarMutationPreview previewEventDeletion(Long accountId, CalendarMutationToolRequest request) {
        EventResponse before = eventService.getEvent(accountId, requireEventId(request));
        return new CalendarMutationPreview(CalendarMutationType.DELETE, CalendarMutationScope.EVENT, before, null);
    }

    private List<EventResponse> applyEventUpdate(Long accountId, CalendarMutationToolRequest request) {
        EventUpdate update = prepareEventUpdate(accountId, request);
        return List.of(eventService.updateEvent(accountId, update.before().id(), update.request()));
    }

    private List<EventResponse> deleteEvent(Long accountId, CalendarMutationToolRequest request) {
        eventService.deleteEvent(accountId, requireEventId(request));
        return List.of();
    }

    private EventUpdate prepareEventUpdate(Long accountId, CalendarMutationToolRequest request) {
        EventResponse before = eventService.getEvent(accountId, requireEventId(request));
        return new EventUpdate(before, updateEventRequest(request, before));
    }

    private CreateEventRequest createEventRequest(CalendarMutationToolRequest request) {
        return new CreateEventRequest(
                requireTitle(request),
                request.description(),
                requireStartAt(request),
                requireEndAt(request),
                requireAllDay(request),
                request.timeZone(),
                request.tagId()
        );
    }

    private UpdateEventRequest updateEventRequest(
            CalendarMutationToolRequest request,
            EventResponse before
    ) {
        return new UpdateEventRequest(
                titleOrExisting(request, before.title()),
                valueOrExisting(request.description(), before.description()),
                valueOrExisting(request.startAt(), before.startAt()),
                valueOrExisting(request.endAt(), before.endAt()),
                valueOrExisting(request.allDay(), before.allDay()),
                valueOrExisting(request.timeZone(), before.timeZone()),
                tagIdOrExisting(request, before)
        );
    }

    private EventResponse eventForCreation(Long accountId, CreateEventRequest request) {
        return new EventResponse(
                null,
                request.title(),
                request.description(),
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone(),
                false,
                null,
                false,
                tagResponse(accountId, request.tagId()),
                null,
                null,
                null
        );
    }

    private EventResponse eventForUpdate(
            Long accountId,
            CalendarMutationToolRequest request,
            EventUpdate update
    ) {
        EventResponse before = update.before();
        UpdateEventRequest eventRequest = update.request();
        return new EventResponse(
                before.id(),
                eventRequest.title(),
                eventRequest.description(),
                eventRequest.startAt(),
                eventRequest.endAt(),
                eventRequest.allDay(),
                eventRequest.timeZone(),
                before.importantEvent(),
                null,
                false,
                tagResponseOrExisting(accountId, request, before),
                null,
                before.createdAt(),
                before.updatedAt()
        );
    }

    private CalendarMutationOperation requireOperation(CalendarMutationToolRequest request) {
        return requireValue(request.operation());
    }

    private String titleOrExisting(CalendarMutationToolRequest request, String existingTitle) {
        return request.title() == null ? existingTitle : requireTitle(request);
    }

    private <T> T valueOrExisting(T requestedValue, T existingValue) {
        return requestedValue == null ? existingValue : requestedValue;
    }

    private Long tagIdOrExisting(CalendarMutationToolRequest request, EventResponse before) {
        if (request.tagId() != null) {
            return request.tagId();
        }
        return before.tag() == null ? null : before.tag().id();
    }

    private TagResponse tagResponseOrExisting(
            Long accountId,
            CalendarMutationToolRequest request,
            EventResponse before
    ) {
        return request.tagId() == null ? before.tag() : tagResponse(accountId, request.tagId());
    }

    private TagResponse tagResponse(Long accountId, Long tagId) {
        return TagResponse.from(tagService.getTagOrDefault(accountId, tagId));
    }

    private Long requireEventId(CalendarMutationToolRequest request) {
        return requireValue(request.eventId());
    }

    private String requireTitle(CalendarMutationToolRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
        return request.title();
    }

    private Instant requireStartAt(CalendarMutationToolRequest request) {
        return requireValue(request.startAt());
    }

    private Instant requireEndAt(CalendarMutationToolRequest request) {
        return requireValue(request.endAt());
    }

    private boolean requireAllDay(CalendarMutationToolRequest request) {
        return requireValue(request.allDay());
    }

    private <T> T requireValue(T value) {
        if (value == null) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
        return value;
    }

    private record EventUpdate(EventResponse before, UpdateEventRequest request) {
    }
}
