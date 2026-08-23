package com.calio.calendar.groupcalendar.sharing.event.controller;

import com.calio.calendar.groupcalendar.sharing.event.controller.dto.SelectedPersonalEventGroupShareRequest;
import com.calio.calendar.groupcalendar.sharing.event.service.PersonalEventGroupShareService;
import com.calio.calendar.groupcalendar.sharing.event.service.dto.SelectedPersonalEventGroupShareCommand;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public void shareSelected(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @Valid @RequestBody SelectedPersonalEventGroupShareRequest request
    ) {
        shareService.shareSelected(
                account.accountId(),
                groupSpaceId,
                new SelectedPersonalEventGroupShareCommand(request.eventIds())
        );
    }
}
