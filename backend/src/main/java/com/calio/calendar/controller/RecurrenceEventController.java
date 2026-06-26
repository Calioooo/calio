package com.calio.calendar.controller;

import com.calio.calendar.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.service.RecurrenceEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            @Valid @RequestBody CreateRecurrenceEventRequest request
    ) {
        RecurrenceEventResponse response = recurrenceEventService.createRecurrenceEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{recurrenceId}")
    public RecurrenceEventResponse getRecurrenceEvent(@PathVariable Long recurrenceId) {
        return recurrenceEventService.getRecurrenceEvent(recurrenceId);
    }

    @DeleteMapping("/{recurrenceId}")
    public ResponseEntity<Void> deleteRecurrenceEvent(@PathVariable Long recurrenceId) {
        recurrenceEventService.deleteRecurrenceEvent(recurrenceId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{recurrenceId}")
    public RecurrenceEventResponse updateRecurrenceEvent(
            @PathVariable Long recurrenceId,
            @Valid @RequestBody UpdateRecurrenceEventRequest request
    ) {
        return recurrenceEventService.updateRecurrenceEvent(recurrenceId, request);
    }

    @PatchMapping("/{recurrenceId}/occurrences/{eventId}")
    public EventResponse updateRecurrenceOccurrence(
            @PathVariable Long recurrenceId,
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateRecurrenceOccurrenceRequest request
    ) {
        return recurrenceEventService.updateRecurrenceOccurrence(recurrenceId, eventId, request);
    }

    @DeleteMapping("/{recurrenceId}/occurrences/{eventId}")
    public ResponseEntity<Void> deleteRecurrenceOccurrence(
            @PathVariable Long recurrenceId,
            @PathVariable Long eventId
    ) {
        recurrenceEventService.deleteRecurrenceOccurrence(recurrenceId, eventId);
        return ResponseEntity.noContent().build();
    }
}
