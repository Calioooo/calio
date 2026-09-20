package com.calio.calendar.singleevent.controller;

import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.sharing.event.controller.dto.CreateEventGroupSharesRequest;
import com.calio.calendar.sharing.event.controller.dto.CreateEventGroupSharesResponse;
import com.calio.calendar.sharing.event.service.PersonalEventGroupShareService;
import com.calio.calendar.singleevent.controller.dto.CreateSingleEventRequest;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.controller.dto.UpdateImportantSingleEventRequest;
import com.calio.calendar.singleevent.controller.dto.UpdateSingleEventRequest;
import com.calio.calendar.singleevent.usecase.CreateSingleEventUseCase;
import com.calio.calendar.singleevent.usecase.DeleteSingleEventUseCase;
import com.calio.calendar.singleevent.usecase.GetSingleEventUseCase;
import com.calio.calendar.singleevent.usecase.ListEventsUseCase;
import com.calio.calendar.singleevent.usecase.UpdateImportantSingleEventUseCase;
import com.calio.calendar.singleevent.usecase.UpdateSingleEventUseCase;
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
public class SingleEventController {

  private final CreateSingleEventUseCase createEventUseCase;
  private final GetSingleEventUseCase getEventUseCase;
  private final UpdateSingleEventUseCase updateEventUseCase;
  private final UpdateImportantSingleEventUseCase updateImportantEventUseCase;
  private final DeleteSingleEventUseCase deleteEventUseCase;
  private final ListEventsUseCase listEventsUseCase;
  private final PersonalEventGroupShareService eventGroupShareService;

  public SingleEventController(
      CreateSingleEventUseCase createEventUseCase,
      GetSingleEventUseCase getEventUseCase,
      UpdateSingleEventUseCase updateEventUseCase,
      UpdateImportantSingleEventUseCase updateImportantEventUseCase,
      DeleteSingleEventUseCase deleteEventUseCase,
      ListEventsUseCase listEventsUseCase,
      PersonalEventGroupShareService eventGroupShareService) {
    this.createEventUseCase = createEventUseCase;
    this.getEventUseCase = getEventUseCase;
    this.updateEventUseCase = updateEventUseCase;
    this.updateImportantEventUseCase = updateImportantEventUseCase;
    this.deleteEventUseCase = deleteEventUseCase;
    this.listEventsUseCase = listEventsUseCase;
    this.eventGroupShareService = eventGroupShareService;
  }

  @PostMapping
  public ResponseEntity<EventResponse> createEvent(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody CreateSingleEventRequest request) {
    EventResponse response = createEventUseCase.create(account.accountId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{eventId}")
  public EventResponse getEvent(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable("eventId") Long eventId) {
    return getEventUseCase.get(account.accountId(), eventId);
  }

  @PutMapping("/{eventId}")
  public EventResponse updateEvent(
      @PathVariable("eventId") Long eventId,
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody UpdateSingleEventRequest request) {
    return updateEventUseCase.update(account.accountId(), eventId, request);
  }

  @PatchMapping("/{eventId}/important-event")
  public EventResponse updateImportantEvent(
      @PathVariable("eventId") Long eventId,
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody UpdateImportantSingleEventRequest request) {
    return updateImportantEventUseCase.update(
        account.accountId(), eventId, request.importantEvent());
  }

  @DeleteMapping("/{eventId}")
  public ResponseEntity<Void> deleteEvent(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable("eventId") Long eventId) {
    deleteEventUseCase.delete(account.accountId(), eventId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/group-shares")
  public ResponseEntity<CreateEventGroupSharesResponse> createGroupShares(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody CreateEventGroupSharesRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(eventGroupShareService.create(account.accountId(), request));
  }

  @GetMapping
  public List<EventResponse> listEvents(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return listEventsUseCase.list(account.accountId(), from, to);
  }
}
