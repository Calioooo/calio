package com.calio.calendar.controller;

import com.calio.calendar.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventCommand;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventRequestFactory;
import com.calio.calendar.controller.dto.UpdateType;
import com.calio.calendar.service.RecurrenceEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@Validated
@RestController
@RequestMapping("/api/recurrence-events")
public class RecurrenceEventController {

    private final RecurrenceEventService recurrenceEventService;
    private final UpdateRecurrenceEventRequestFactory updateRequestFactory;

    public RecurrenceEventController(
            RecurrenceEventService recurrenceEventService,
            UpdateRecurrenceEventRequestFactory updateRequestFactory
    ) {
        this.recurrenceEventService = recurrenceEventService;
        this.updateRequestFactory = updateRequestFactory;
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

    @PatchMapping("/{recurrenceId}")
    public ResponseEntity<?> updateRecurrenceEvent(
            @PathVariable Long recurrenceId,
            @RequestParam(required = false) String updateScope,
            @RequestBody JsonNode body
    ) {
        UpdateRecurrenceEventCommand command = updateRequestFactory.create(body, updateScope);
        if (command.updateType() == UpdateType.WHOLE) {
            RecurrenceEventResponse response = recurrenceEventService.updateWholeRecurrenceEvent(recurrenceId, command);
            return ResponseEntity.ok(response);
        }

        EventResponse response = recurrenceEventService.updateSingleOccurrence(recurrenceId, command.request());
        return ResponseEntity.ok(response);
    }
}
