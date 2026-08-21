package com.calio.calendar.groupcalendar.event.controller;

import com.calio.calendar.groupcalendar.event.controller.dto.GroupCalendarEventRequest;
import com.calio.calendar.groupcalendar.event.controller.dto.GroupCalendarEventResponse;
import com.calio.calendar.groupcalendar.event.service.GroupCalendarEventService;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-spaces/{groupSpaceId}/events")
public class GroupCalendarEventController {

    private final GroupCalendarEventService groupCalendarEventService;

    public GroupCalendarEventController(GroupCalendarEventService groupCalendarEventService) {
        this.groupCalendarEventService = groupCalendarEventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupCalendarEventResponse create(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @Valid @RequestBody GroupCalendarEventRequest request
    ) {
        return groupCalendarEventService.create(account.accountId(), groupSpaceId, request);
    }

    @GetMapping("/{eventId}")
    public GroupCalendarEventResponse get(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long eventId
    ) {
        return groupCalendarEventService.get(account.accountId(), groupSpaceId, eventId);
    }

    @PatchMapping("/{eventId}")
    public GroupCalendarEventResponse update(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long eventId,
            @Valid @RequestBody GroupCalendarEventRequest request
    ) {
        return groupCalendarEventService.update(account.accountId(), groupSpaceId, eventId, request);
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long eventId
    ) {
        groupCalendarEventService.delete(account.accountId(), groupSpaceId, eventId);
    }
}
