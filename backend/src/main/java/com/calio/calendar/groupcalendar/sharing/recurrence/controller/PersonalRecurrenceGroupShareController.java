package com.calio.calendar.groupcalendar.sharing.recurrence.controller;

import com.calio.calendar.groupcalendar.sharing.recurrence.controller.dto.PersonalRecurrenceGroupShareRequest;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.PersonalRecurrenceGroupShareService;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.dto.PersonalRecurrenceGroupShareCommand;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-spaces/{groupSpaceId}/recurrence-shares")
public class PersonalRecurrenceGroupShareController {

    private final PersonalRecurrenceGroupShareService shareService;

    public PersonalRecurrenceGroupShareController(PersonalRecurrenceGroupShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void share(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @Valid @RequestBody PersonalRecurrenceGroupShareRequest request
    ) {
        shareService.share(
                account.accountId(),
                groupSpaceId,
                new PersonalRecurrenceGroupShareCommand(
                        request.recurrenceId(),
                        request.selectionEnabled(),
                        request.originStartAtsOrEmpty()
                )
        );
    }
}
