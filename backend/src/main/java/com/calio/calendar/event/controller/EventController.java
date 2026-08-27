package com.calio.calendar.event.controller;

import com.calio.calendar.event.controller.dto.CreateEventRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.controller.dto.UpdateImportantEventRequest;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.sharing.event.controller.dto.CreateEventGroupSharesRequest;
import com.calio.calendar.sharing.event.controller.dto.CreateEventGroupSharesResponse;
import com.calio.calendar.sharing.event.service.PersonalEventGroupShareService;
import com.calio.calendar.sharing.controller.dto.GroupShareStatusResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final PersonalEventGroupShareService eventGroupShareService;

    public EventController(
            EventService eventService,
            PersonalEventGroupShareService eventGroupShareService
    ) {
        this.eventService = eventService;
        this.eventGroupShareService = eventGroupShareService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CreateEventRequest request
    ) {
        EventResponse response = eventService.createEvent(account.accountId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("eventId") Long eventId
    ) {
        return eventService.getEvent(account.accountId(), eventId);
    }

    @PutMapping("/{eventId}")
    public EventResponse updateEvent(
            @PathVariable("eventId") Long eventId,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody UpdateEventRequest request
    ) {
        return eventService.updateEvent(account.accountId(), eventId, request);
    }

    @PatchMapping("/{eventId}/important-event")
    public EventResponse updateImportantEvent(
            @PathVariable("eventId") Long eventId,
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody UpdateImportantEventRequest request
    ) {
        return eventService.updateImportantEvent(account.accountId(), eventId, request);
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> deleteEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("eventId") Long eventId
    ) {
        eventService.deleteEvent(account.accountId(), eventId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/group-shares")
    public ResponseEntity<CreateEventGroupSharesResponse> createGroupShares(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CreateEventGroupSharesRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventGroupShareService.create(account.accountId(), request));
    }

    @GetMapping("/{eventId}/group-shares")
    public List<GroupShareStatusResponse> listGroupShares(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("eventId") Long eventId
    ) {
        return eventGroupShareService.list(account.accountId(), eventId);
    }

    @GetMapping
    public List<EventResponse> listEvents(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return eventService.listEvents(account.accountId(), from, to);
    }
}
