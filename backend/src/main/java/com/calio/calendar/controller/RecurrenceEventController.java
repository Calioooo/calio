package com.calio.calendar.controller;

import com.calio.calendar.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventRequestFactory;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventValidator;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.service.RecurrenceEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final UpdateRecurrenceEventValidator updateRecurrenceEventValidator;
    private final UpdateRecurrenceEventRequestFactory updateRecurrenceEventRequestFactory;

    public RecurrenceEventController(
            RecurrenceEventService recurrenceEventService,
            UpdateRecurrenceEventValidator updateRecurrenceEventValidator,
            UpdateRecurrenceEventRequestFactory updateRecurrenceEventRequestFactory
    ) {
        this.recurrenceEventService = recurrenceEventService;
        this.updateRecurrenceEventValidator = updateRecurrenceEventValidator;
        this.updateRecurrenceEventRequestFactory = updateRecurrenceEventRequestFactory;
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

    @PutMapping("/{recurrenceId}")
    public RecurrenceEventResponse updateRecurrenceEvent(
            @PathVariable Long recurrenceId,
            @RequestParam(required = false) String updateScope,
            @RequestBody(required = false) JsonNode requestBody
    ) {
        if (updateScope != null) {
            throw new CalioException(ErrorCode.RECURRENCE_UPDATE_SCOPE_UNSUPPORTED);
        }

        updateRecurrenceEventValidator.validate(requestBody);
        UpdateRecurrenceEventRequest request = updateRecurrenceEventRequestFactory.create(requestBody);
        return recurrenceEventService.updateRecurrenceEvent(recurrenceId, request);
    }
}
