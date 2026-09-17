package com.calio.calendar.recurrence.controller;

import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.usecase.CreateRecurrenceEventUseCase;
import com.calio.calendar.recurrence.usecase.DeleteRecurrenceEventUseCase;
import com.calio.calendar.recurrence.usecase.DeleteRecurrenceOccurrenceUseCase;
import com.calio.calendar.recurrence.usecase.GetRecurrenceEventUseCase;
import com.calio.calendar.recurrence.usecase.UpdateRecurrenceEventUseCase;
import com.calio.calendar.recurrence.usecase.UpdateRecurrenceOccurrenceUseCase;
import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.sharing.recurrence.controller.dto.CreateRecurrenceGroupSharesRequest;
import com.calio.calendar.sharing.recurrence.controller.dto.CreateRecurrenceGroupSharesResponse;
import com.calio.calendar.sharing.recurrence.service.PersonalRecurrenceGroupShareService;
import jakarta.validation.Valid;
import java.time.Instant;
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
@RequestMapping("/api/recurrence-events")
public class RecurrenceEventController {

  private final CreateRecurrenceEventUseCase createRecurrenceEventUseCase;
  private final GetRecurrenceEventUseCase getRecurrenceEventUseCase;
  private final UpdateRecurrenceEventUseCase updateRecurrenceEventUseCase;
  private final UpdateRecurrenceOccurrenceUseCase updateRecurrenceOccurrenceUseCase;
  private final DeleteRecurrenceEventUseCase deleteRecurrenceEventUseCase;
  private final DeleteRecurrenceOccurrenceUseCase deleteRecurrenceOccurrenceUseCase;
  private final PersonalRecurrenceGroupShareService recurrenceGroupShareService;

  public RecurrenceEventController(
      CreateRecurrenceEventUseCase createRecurrenceEventUseCase,
      GetRecurrenceEventUseCase getRecurrenceEventUseCase,
      UpdateRecurrenceEventUseCase updateRecurrenceEventUseCase,
      UpdateRecurrenceOccurrenceUseCase updateRecurrenceOccurrenceUseCase,
      DeleteRecurrenceEventUseCase deleteRecurrenceEventUseCase,
      DeleteRecurrenceOccurrenceUseCase deleteRecurrenceOccurrenceUseCase,
      PersonalRecurrenceGroupShareService recurrenceGroupShareService) {
    this.createRecurrenceEventUseCase = createRecurrenceEventUseCase;
    this.getRecurrenceEventUseCase = getRecurrenceEventUseCase;
    this.updateRecurrenceEventUseCase = updateRecurrenceEventUseCase;
    this.updateRecurrenceOccurrenceUseCase = updateRecurrenceOccurrenceUseCase;
    this.deleteRecurrenceEventUseCase = deleteRecurrenceEventUseCase;
    this.deleteRecurrenceOccurrenceUseCase = deleteRecurrenceOccurrenceUseCase;
    this.recurrenceGroupShareService = recurrenceGroupShareService;
  }

  @PostMapping
  public ResponseEntity<RecurrenceEventResponse> createRecurrenceEvent(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody CreateRecurrenceEventRequest request) {
    RecurrenceEventResponse response =
        createRecurrenceEventUseCase.create(account.accountId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{recurrenceId}")
  public RecurrenceEventResponse getRecurrenceEvent(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable("recurrenceId") Long recurrenceId) {
    return getRecurrenceEventUseCase.get(account.accountId(), recurrenceId);
  }

  @DeleteMapping("/{recurrenceId}")
  public ResponseEntity<Void> deleteRecurrenceEvent(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable("recurrenceId") Long recurrenceId) {
    deleteRecurrenceEventUseCase.delete(account.accountId(), recurrenceId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{recurrenceId}/group-shares")
  public ResponseEntity<CreateRecurrenceGroupSharesResponse> createGroupShares(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable("recurrenceId") Long recurrenceId,
      @Valid @RequestBody CreateRecurrenceGroupSharesRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(recurrenceGroupShareService.create(account.accountId(), recurrenceId, request));
  }

  @PutMapping("/{recurrenceId}")
  public RecurrenceEventResponse updateRecurrenceEvent(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable("recurrenceId") Long recurrenceId,
      @Valid @RequestBody UpdateRecurrenceEventRequest request) {
    return updateRecurrenceEventUseCase.update(account.accountId(), recurrenceId, request);
  }

  @PatchMapping("/{recurrenceId}/occurrences")
  public EventResponse updateRecurrenceOccurrence(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable("recurrenceId") Long recurrenceId,
      @Valid @RequestBody UpdateRecurrenceOccurrenceRequest request) {
    return updateRecurrenceOccurrenceUseCase.update(account.accountId(), recurrenceId, request);
  }

  @DeleteMapping("/{recurrenceId}/occurrences")
  public ResponseEntity<Void> deleteRecurrenceOccurrence(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @PathVariable("recurrenceId") Long recurrenceId,
      @RequestParam("originStartAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant originStartAt) {
    deleteRecurrenceOccurrenceUseCase.delete(account.accountId(), recurrenceId, originStartAt);
    return ResponseEntity.noContent().build();
  }
}
