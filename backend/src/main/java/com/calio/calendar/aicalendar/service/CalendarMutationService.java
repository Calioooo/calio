package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.domain.CalendarMutationOperation;
import com.calio.calendar.aicalendar.domain.CalendarMutationScope;
import com.calio.calendar.aicalendar.domain.CalendarMutationType;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationPreview;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationRecurrencePreview;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarMutationToolRequest;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.CreateEventRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.service.RecurrenceEventService;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.service.TagService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CalendarMutationService {

    private final EventService eventService;
    private final RecurrenceEventService recurrenceEventService;
    private final TagService tagService;

    public CalendarMutationService(
            EventService eventService,
            RecurrenceEventService recurrenceEventService,
            TagService tagService
    ) {
        this.eventService = eventService;
        this.recurrenceEventService = recurrenceEventService;
        this.tagService = tagService;
    }

    public CalendarMutationPreview preview(Long accountId, CalendarMutationToolRequest request) {
        return switch (requireOperation(request)) {
            case CREATE_EVENT -> previewEventCreation(accountId, request);
            case UPDATE_EVENT -> previewEventUpdate(accountId, request);
            case DELETE_EVENT -> previewEventDeletion(accountId, request);
            case UPDATE_RECURRENCE_OCCURRENCE -> previewOccurrenceUpdate(accountId, request);
            case DELETE_RECURRENCE_OCCURRENCE -> previewOccurrenceDeletion(accountId, request);
            case UPDATE_RECURRENCE_SERIES -> previewSeriesUpdate(accountId, request);
            case DELETE_RECURRENCE_SERIES -> previewSeriesDeletion(accountId, request);
        };
    }

    public List<EventResponse> apply(Long accountId, CalendarMutationToolRequest request) {
        return switch (requireOperation(request)) {
            case CREATE_EVENT -> List.of(eventService.createEvent(accountId, createEventRequest(request)));
            case UPDATE_EVENT -> applyEventUpdate(accountId, request);
            case DELETE_EVENT -> deleteEvent(accountId, request);
            case UPDATE_RECURRENCE_OCCURRENCE -> applyOccurrenceUpdate(accountId, request);
            case DELETE_RECURRENCE_OCCURRENCE -> deleteOccurrence(accountId, request);
            case UPDATE_RECURRENCE_SERIES -> applySeriesUpdate(accountId, request);
            case DELETE_RECURRENCE_SERIES -> deleteSeries(accountId, request);
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

    private CalendarMutationPreview previewOccurrenceUpdate(
            Long accountId,
            CalendarMutationToolRequest request
    ) {
        EventResponse before = getOccurrence(accountId, request);
        rejectOccurrenceTagChange(request, before);
        UpdateRecurrenceOccurrenceRequest occurrenceRequest = updateOccurrenceRequest(request, before);
        return new CalendarMutationPreview(
                CalendarMutationType.UPDATE,
                CalendarMutationScope.THIS_OCCURRENCE,
                before,
                eventForOccurrenceUpdate(before, occurrenceRequest)
        );
    }

    private CalendarMutationPreview previewOccurrenceDeletion(
            Long accountId,
            CalendarMutationToolRequest request
    ) {
        return new CalendarMutationPreview(
                CalendarMutationType.DELETE,
                CalendarMutationScope.THIS_OCCURRENCE,
                getOccurrence(accountId, request),
                null
        );
    }

    private CalendarMutationPreview previewSeriesUpdate(Long accountId, CalendarMutationToolRequest request) {
        RecurrenceEventResponse before = recurrenceEventService.getRecurrenceEvent(
                accountId,
                requireRecurrenceId(request)
        );
        UpdateRecurrenceEventRequest seriesRequest = updateSeriesRequest(request, before);
        return new CalendarMutationPreview(
                CalendarMutationType.UPDATE,
                CalendarMutationScope.ENTIRE_SERIES,
                eventForSeries(before),
                eventForSeriesUpdate(accountId, request, before, seriesRequest),
                new CalendarMutationRecurrencePreview(before.recurrence(), seriesRequest.recurrence())
        );
    }

    private CalendarMutationPreview previewSeriesDeletion(Long accountId, CalendarMutationToolRequest request) {
        RecurrenceEventResponse before = recurrenceEventService.getRecurrenceEvent(
                accountId,
                requireRecurrenceId(request)
        );
        return new CalendarMutationPreview(
                CalendarMutationType.DELETE,
                CalendarMutationScope.ENTIRE_SERIES,
                eventForSeries(before),
                null
        );
    }

    private List<EventResponse> applyOccurrenceUpdate(Long accountId, CalendarMutationToolRequest request) {
        EventResponse before = getOccurrence(accountId, request);
        rejectOccurrenceTagChange(request, before);
        return List.of(recurrenceEventService.updateRecurrenceOccurrence(
                accountId,
                requireRecurrenceId(request),
                updateOccurrenceRequest(request, before)
        ));
    }

    private List<EventResponse> deleteOccurrence(Long accountId, CalendarMutationToolRequest request) {
        recurrenceEventService.deleteRecurrenceOccurrence(
                accountId,
                requireRecurrenceId(request),
                requireOriginStartAt(request)
        );
        return List.of();
    }

    private List<EventResponse> applySeriesUpdate(Long accountId, CalendarMutationToolRequest request) {
        RecurrenceEventResponse before = recurrenceEventService.getRecurrenceEvent(
                accountId,
                requireRecurrenceId(request)
        );
        RecurrenceEventResponse updated = recurrenceEventService.updateRecurrenceEvent(
                accountId,
                requireRecurrenceId(request),
                updateSeriesRequest(request, before)
        );
        return List.of(eventForSeries(updated));
    }

    private List<EventResponse> deleteSeries(Long accountId, CalendarMutationToolRequest request) {
        recurrenceEventService.deleteRecurrenceEvent(accountId, requireRecurrenceId(request));
        return List.of();
    }

    private EventResponse getOccurrence(Long accountId, CalendarMutationToolRequest request) {
        Long recurrenceId = requireRecurrenceId(request);
        Instant originStartAt = requireOriginStartAt(request);
        return eventService.listEvents(
                        accountId,
                        originStartAt.minus(Duration.ofDays(1)),
                        originStartAt.plus(Duration.ofDays(1))
                )
                .stream()
                .filter(EventResponse::isRecurrenceOccurrence)
                .filter(event -> recurrenceId.equals(event.recurrenceId()))
                .filter(event -> originStartAt.equals(event.originStartAt()))
                .findFirst()
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND));
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

    private UpdateRecurrenceOccurrenceRequest updateOccurrenceRequest(
            CalendarMutationToolRequest request,
            EventResponse before
    ) {
        return new UpdateRecurrenceOccurrenceRequest(
                requireOriginStartAt(request),
                titleOrExisting(request, before.title()),
                valueOrExisting(request.description(), before.description()),
                valueOrExisting(request.startAt(), before.startAt()),
                valueOrExisting(request.endAt(), before.endAt()),
                valueOrExisting(request.allDay(), before.allDay()),
                valueOrExisting(request.timeZone(), before.timeZone())
        );
    }

    private UpdateRecurrenceEventRequest updateSeriesRequest(
            CalendarMutationToolRequest request,
            RecurrenceEventResponse before
    ) {
        return new UpdateRecurrenceEventRequest(
                titleOrExisting(request, before.title()),
                valueOrExisting(request.description(), before.description()),
                valueOrExisting(request.allDay(), before.allDay()),
                valueOrExisting(request.startAt(), before.firstOccurrenceStartAt()),
                valueOrExisting(request.endAt(), before.firstOccurrenceEndAt()),
                valueOrExisting(request.timeZone(), before.timeZone()),
                recurrenceRulesOrExisting(request, before.recurrence()),
                tagIdOrExisting(request, eventForSeries(before))
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

    private EventResponse eventForOccurrenceUpdate(
            EventResponse before,
            UpdateRecurrenceOccurrenceRequest request
    ) {
        return new EventResponse(
                null,
                request.title(),
                request.description(),
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone(),
                false,
                before.recurrenceId(),
                true,
                before.tag(),
                before.originStartAt(),
                before.createdAt(),
                before.updatedAt()
        );
    }

    private EventResponse eventForSeries(RecurrenceEventResponse recurrenceEvent) {
        return new EventResponse(
                null,
                recurrenceEvent.title(),
                recurrenceEvent.description(),
                recurrenceEvent.firstOccurrenceStartAt(),
                recurrenceEvent.firstOccurrenceEndAt(),
                recurrenceEvent.allDay(),
                recurrenceEvent.timeZone(),
                false,
                recurrenceEvent.recurrenceId(),
                true,
                recurrenceEvent.tag(),
                recurrenceEvent.firstOccurrenceStartAt(),
                recurrenceEvent.createdAt(),
                recurrenceEvent.updatedAt()
        );
    }

    private EventResponse eventForSeriesUpdate(
            Long accountId,
            CalendarMutationToolRequest request,
            RecurrenceEventResponse before,
            UpdateRecurrenceEventRequest seriesRequest
    ) {
        return new EventResponse(
                null,
                seriesRequest.title(),
                seriesRequest.description(),
                seriesRequest.firstOccurrenceStartAt(),
                seriesRequest.firstOccurrenceEndAt(),
                seriesRequest.allDay(),
                seriesRequest.timeZone(),
                false,
                before.recurrenceId(),
                true,
                tagResponseOrExisting(accountId, request, eventForSeries(before)),
                seriesRequest.firstOccurrenceStartAt(),
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

    private void rejectOccurrenceTagChange(
            CalendarMutationToolRequest request,
            EventResponse before
    ) {
        Long existingTagId = before.tag() == null ? null : before.tag().id();
        if (request.tagId() != null && !request.tagId().equals(existingTagId)) {
            throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_TAG_CHANGE_NOT_SUPPORTED);
        }
    }

    private List<String> recurrenceRulesOrExisting(
            CalendarMutationToolRequest request,
            List<String> existingRules
    ) {
        return request.recurrenceRules() == null || request.recurrenceRules().isEmpty()
                ? existingRules
                : request.recurrenceRules();
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

    private Long requireRecurrenceId(CalendarMutationToolRequest request) {
        return requireValue(request.recurrenceId());
    }

    private Instant requireOriginStartAt(CalendarMutationToolRequest request) {
        return requireValue(request.originStartAt());
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
