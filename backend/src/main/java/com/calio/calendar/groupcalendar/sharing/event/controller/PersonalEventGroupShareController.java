package com.calio.calendar.groupcalendar.sharing.event.controller;

import com.calio.calendar.groupcalendar.sharing.event.controller.dto.PersonalEventGroupShareRequest;
import com.calio.calendar.groupcalendar.sharing.event.controller.dto.UpdatePersonalEventGroupShareRequest;
import com.calio.calendar.groupcalendar.sharing.event.service.PersonalEventGroupShareService;
import com.calio.calendar.groupcalendar.sharing.event.service.dto.PersonalEventGroupShareCommand;
import com.calio.calendar.groupcalendar.sharing.event.service.dto.UpdatePersonalEventGroupShareCommand;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-spaces/{groupSpaceId}/event-shares")
public class PersonalEventGroupShareController {

    private final PersonalEventGroupShareService shareService;

    public PersonalEventGroupShareController(PersonalEventGroupShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void share(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @Valid @RequestBody PersonalEventGroupShareRequest request
    ) {
        shareService.share(
                account.accountId(),
                groupSpaceId,
                new PersonalEventGroupShareCommand(
                        request.selectionEnabled(),
                        request.eventIdsOrEmpty()
                )
        );
    }

    @PatchMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long eventId,
            @Valid @RequestBody UpdatePersonalEventGroupShareRequest request
    ) {
        shareService.update(
                account.accountId(),
                groupSpaceId,
                eventId,
                new UpdatePersonalEventGroupShareCommand(
                        request.showOriginalDetails(),
                        request.overrideTitle(),
                        request.overrideStartAt(),
                        request.overrideEndAt(),
                        request.overrideAllDay()
                )
        );
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long eventId
    ) {
        shareService.remove(account.accountId(), groupSpaceId, eventId);
    }
}
