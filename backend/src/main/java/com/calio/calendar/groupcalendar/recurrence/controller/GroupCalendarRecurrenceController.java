package com.calio.calendar.groupcalendar.recurrence.controller;

import com.calio.calendar.groupcalendar.recurrence.controller.dto.GroupCalendarRecurrenceRequest;
import com.calio.calendar.groupcalendar.recurrence.controller.dto.GroupCalendarRecurrenceResponse;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceService;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-spaces/{groupSpaceId}/recurrence-events")
public class GroupCalendarRecurrenceController {

    private final GroupCalendarRecurrenceService recurrenceService;

    public GroupCalendarRecurrenceController(GroupCalendarRecurrenceService recurrenceService) {
        this.recurrenceService = recurrenceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupCalendarRecurrenceResponse create(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @Valid @RequestBody GroupCalendarRecurrenceRequest request
    ) {
        return recurrenceService.create(account.accountId(), groupSpaceId, request);
    }

    @GetMapping("/{recurrenceId}")
    public GroupCalendarRecurrenceResponse get(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long recurrenceId
    ) {
        return recurrenceService.get(account.accountId(), groupSpaceId, recurrenceId);
    }

    @PutMapping("/{recurrenceId}")
    public GroupCalendarRecurrenceResponse update(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long recurrenceId,
            @Valid @RequestBody GroupCalendarRecurrenceRequest request
    ) {
        return recurrenceService.update(account.accountId(), groupSpaceId, recurrenceId, request);
    }

    @DeleteMapping("/{recurrenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long recurrenceId
    ) {
        recurrenceService.delete(account.accountId(), groupSpaceId, recurrenceId);
    }
}
