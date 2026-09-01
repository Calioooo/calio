package com.calio.calendar.groupcalendar.controller;

import com.calio.calendar.groupcalendar.controller.dto.GroupCalendarItemResponse;
import com.calio.calendar.groupcalendar.controller.dto.SharedCalendarItemResponse;
import com.calio.calendar.groupcalendar.service.GroupCalendarService;
import com.calio.calendar.security.AuthenticatedAccount;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-spaces/{groupSpaceId}/calendar")
public class GroupCalendarController {

    private final GroupCalendarService groupCalendarService;

    public GroupCalendarController(GroupCalendarService groupCalendarService) {
        this.groupCalendarService = groupCalendarService;
    }

    @GetMapping("/group-items")
    public List<GroupCalendarItemResponse> listGroupItems(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return groupCalendarService.listGroupItems(account.accountId(), groupSpaceId, from, to);
    }

    @GetMapping("/shared-items")
    public List<SharedCalendarItemResponse> listSharedItems(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return groupCalendarService.listSharedItems(account.accountId(), groupSpaceId, from, to);
    }
}
