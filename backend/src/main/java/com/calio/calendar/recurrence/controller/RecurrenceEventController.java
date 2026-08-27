package com.calio.calendar.recurrence.controller;

import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.service.RecurrenceEventService;
import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.sharing.recurrence.controller.dto.CreateRecurrenceGroupSharesRequest;
import com.calio.calendar.sharing.recurrence.controller.dto.CreateRecurrenceGroupSharesResponse;
import com.calio.calendar.sharing.recurrence.service.PersonalRecurrenceGroupShareService;
import com.calio.calendar.sharing.controller.dto.GroupShareStatusResponse;
import com.calio.calendar.sharing.controller.dto.UpdateGroupShareAnonymousRequest;
import java.util.List;
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

    private final RecurrenceEventService recurrenceEventService;
    private final PersonalRecurrenceGroupShareService recurrenceGroupShareService;

    public RecurrenceEventController(
            RecurrenceEventService recurrenceEventService,
            PersonalRecurrenceGroupShareService recurrenceGroupShareService
    ) {
        this.recurrenceEventService = recurrenceEventService;
        this.recurrenceGroupShareService = recurrenceGroupShareService;
    }

    @PostMapping
    public ResponseEntity<RecurrenceEventResponse> createRecurrenceEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CreateRecurrenceEventRequest request
    ) {
        RecurrenceEventResponse response = recurrenceEventService.createRecurrenceEvent(
                account.accountId(), request
        );
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

    @PostMapping("/{recurrenceId}/group-shares")
    public ResponseEntity<CreateRecurrenceGroupSharesResponse> createGroupShares(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @Valid @RequestBody CreateRecurrenceGroupSharesRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recurrenceGroupShareService.create(account.accountId(), recurrenceId, request));
    }

    @GetMapping("/{recurrenceId}/group-shares")
    public List<GroupShareStatusResponse> listGroupShares(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId
    ) {
        return recurrenceGroupShareService.list(account.accountId(), recurrenceId);
    }

    @PatchMapping("/{recurrenceId}/group-shares/{groupSpaceId}")
    public GroupShareStatusResponse changeGroupShareAnonymous(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @PathVariable("groupSpaceId") Long groupSpaceId,
            @Valid @RequestBody UpdateGroupShareAnonymousRequest request
    ) {
        return recurrenceGroupShareService.changeAnonymous(
                account.accountId(), recurrenceId, groupSpaceId, request
        );
    }

    @DeleteMapping("/{recurrenceId}/group-shares/{groupSpaceId}")
    public ResponseEntity<Void> removeGroupShare(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @PathVariable("groupSpaceId") Long groupSpaceId
    ) {
        recurrenceGroupShareService.remove(account.accountId(), recurrenceId, groupSpaceId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{recurrenceId}")
    public RecurrenceEventResponse updateRecurrenceEvent(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @Valid @RequestBody UpdateRecurrenceEventRequest request
    ) {
        return recurrenceEventService.updateRecurrenceEvent(account.accountId(), recurrenceId, request);
    }

    @PatchMapping("/{recurrenceId}/occurrences")
    public EventResponse updateRecurrenceOccurrence(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @Valid @RequestBody UpdateRecurrenceOccurrenceRequest request
    ) {
        return recurrenceEventService.updateRecurrenceOccurrence(account.accountId(), recurrenceId, request);
    }

    @DeleteMapping("/{recurrenceId}/occurrences")
    public ResponseEntity<Void> deleteRecurrenceOccurrence(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable("recurrenceId") Long recurrenceId,
            @RequestParam("originStartAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant originStartAt
    ) {
        recurrenceEventService.deleteRecurrenceOccurrence(account.accountId(), recurrenceId, originStartAt);
        return ResponseEntity.noContent().build();
    }
}
