package com.calio.calendar.controller;

import com.calio.calendar.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.service.RecurrenceEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/recurrence-events")
public class RecurrenceEventController {

    private final RecurrenceEventService recurrenceEventService;

    public RecurrenceEventController(RecurrenceEventService recurrenceEventService) {
        this.recurrenceEventService = recurrenceEventService;
    }

    @PostMapping
    public ResponseEntity<RecurrenceEventResponse> createRecurrenceEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CreateRecurrenceEventRequest request
    ) {
        RecurrenceEventResponse response = recurrenceEventService.createRecurrenceEvent(account.accountId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{recurrenceId}")
    public RecurrenceEventResponse getRecurrenceEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId
    ) {
        return recurrenceEventService.getRecurrenceEvent(account.accountId(), recurrenceId);
    }

    @DeleteMapping("/{recurrenceId}")
    public ResponseEntity<Void> deleteRecurrenceEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId
    ) {
        recurrenceEventService.deleteRecurrenceEvent(account.accountId(), recurrenceId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{recurrenceId}")
    public RecurrenceEventResponse updateRecurrenceEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @Valid @RequestBody UpdateRecurrenceEventRequest request
    ) {
        return recurrenceEventService.updateRecurrenceEvent(account.accountId(), recurrenceId, request);
    }

    @PatchMapping("/{recurrenceId}/occurrences/{eventId}")
    public EventResponse updateRecurrenceOccurrence(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @PathVariable("eventId") Long eventId,
            @Valid @RequestBody UpdateRecurrenceOccurrenceRequest request
    ) {
        return recurrenceEventService.updateRecurrenceOccurrence(account.accountId(), recurrenceId, eventId, request);
    }

    @DeleteMapping("/{recurrenceId}/occurrences/{eventId}")
    public ResponseEntity<Void> deleteRecurrenceOccurrence(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @PathVariable("eventId") Long eventId
    ) {
        recurrenceEventService.deleteRecurrenceOccurrence(account.accountId(), recurrenceId, eventId);
        return ResponseEntity.noContent().build();
    }
}
