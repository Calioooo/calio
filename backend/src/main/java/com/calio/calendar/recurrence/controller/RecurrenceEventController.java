package com.calio.calendar.recurrence.controller;

import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.service.RecurrenceService;
import com.calio.calendar.security.AuthenticatedAccount;
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

    private final RecurrenceService recurrenceService;

    public RecurrenceEventController(RecurrenceService recurrenceService) {
        this.recurrenceService = recurrenceService;
    }

    @PostMapping
    public ResponseEntity<RecurrenceEventResponse> createRecurrenceEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CreateRecurrenceEventRequest request
    ) {
        RecurrenceEventResponse response = recurrenceService.createRecurrenceEvent(
                account.accountId(), request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{recurrenceId}")
    public RecurrenceEventResponse getRecurrenceEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId
    ) {
        return recurrenceService.getRecurrenceEvent(account.accountId(), recurrenceId);
    }

    @DeleteMapping("/{recurrenceId}")
    public ResponseEntity<Void> deleteRecurrenceEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId
    ) {
        recurrenceService.deleteRecurrenceEvent(account.accountId(), recurrenceId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{recurrenceId}")
    public RecurrenceEventResponse updateRecurrenceEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @Valid @RequestBody UpdateRecurrenceEventRequest request
    ) {
        return recurrenceService.updateRecurrenceEvent(account.accountId(), recurrenceId, request);
    }

    @PatchMapping("/{recurrenceId}/occurrences")
    public EventResponse updateRecurrenceOccurrence(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @Valid @RequestBody UpdateRecurrenceOccurrenceRequest request
    ) {
        return recurrenceService.updateRecurrenceOccurrence(account.accountId(), recurrenceId, request);
    }

    @DeleteMapping("/{recurrenceId}/occurrences")
    public ResponseEntity<Void> deleteRecurrenceOccurrence(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @RequestParam("originStartAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant originStartAt
    ) {
        recurrenceService.deleteRecurrenceOccurrence(account.accountId(), recurrenceId, originStartAt);
        return ResponseEntity.noContent().build();
    }
}
