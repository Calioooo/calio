package com.calio.calendar.groupcalendar.sharing.event.controller;

import com.calio.calendar.groupcalendar.sharing.event.controller.dto.PersonalEventGroupShareRequest;
import com.calio.calendar.groupcalendar.sharing.event.controller.dto.UpdatePersonalEventGroupShareAnonymousRequest;
import com.calio.calendar.groupcalendar.sharing.event.service.PersonalEventGroupShareService;
import com.calio.calendar.groupcalendar.sharing.event.service.dto.PersonalEventGroupShareCommand;
import com.calio.calendar.groupcalendar.sharing.event.service.dto.PersonalEventGroupShareResult;
import com.calio.calendar.groupcalendar.sharing.event.service.dto.PersonalEventGroupShareStatusResponse;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event-shares")
public class PersonalEventGroupShareController {

    private final PersonalEventGroupShareService shareService;

    public PersonalEventGroupShareController(PersonalEventGroupShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/events/{eventId}")
    public java.util.List<PersonalEventGroupShareStatusResponse> listStatuses(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long eventId
    ) {
        return shareService.listStatuses(account.accountId(), eventId);
    }

    @PatchMapping("/{groupSpaceId}/{eventId}/anonymous")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeAnonymous(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long eventId,
            @Valid @RequestBody UpdatePersonalEventGroupShareAnonymousRequest request
    ) {
        shareService.changeAnonymous(account.accountId(), groupSpaceId, eventId, request.anonymous());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public PersonalEventGroupShareResult share(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody PersonalEventGroupShareRequest request
    ) {
        return shareService.share(
                account.accountId(),
                new PersonalEventGroupShareCommand(
                        request.selectionEnabled(),
                        request.eventIdsOrEmpty(),
                        request.groupSpaceIdsOrEmpty()
                )
        );
    }

    @DeleteMapping("/{groupSpaceId}/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long eventId
    ) {
        shareService.remove(account.accountId(), groupSpaceId, eventId);
    }
}
