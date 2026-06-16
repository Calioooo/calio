package com.calio.calendar.controller;

import com.calio.calendar.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventScope;
import com.calio.calendar.controller.dto.RecurrenceEventResponse;
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

    @PatchMapping("/{recurrenceId}")
    public Object updateRecurrenceEvent(
            @PathVariable Long recurrenceId,
            @RequestParam UpdateRecurrenceEventScope updateScope,
            @RequestBody JsonNode requestBody
    ) {
        UpdateRecurrenceEventRequest request = UpdateRecurrenceEventRequest.from(requestBody);

        return switch (updateScope) {
            case RECURRENCE_EVENT -> recurrenceEventService.updateRecurrenceEvent(recurrenceId, request);
            case SINGLE_OCCURRENCE -> recurrenceEventService.updateSingleOccurrence(recurrenceId, request);
        };
    }
}
