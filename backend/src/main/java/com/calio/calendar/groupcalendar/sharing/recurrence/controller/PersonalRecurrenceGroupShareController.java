package com.calio.calendar.groupcalendar.sharing.recurrence.controller;

import com.calio.calendar.groupcalendar.sharing.recurrence.controller.dto.PersonalRecurrenceGroupShareRequest;
import com.calio.calendar.groupcalendar.sharing.recurrence.controller.dto.UpdatePersonalRecurrenceGroupShareOccurrenceOverrideRequest;
import com.calio.calendar.groupcalendar.sharing.recurrence.controller.dto.UpdatePersonalRecurrenceGroupShareRequest;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.PersonalRecurrenceGroupShareService;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.dto.PersonalRecurrenceGroupShareCommand;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.dto.UpdatePersonalRecurrenceGroupShareCommand;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.dto.UpdatePersonalRecurrenceGroupShareOccurrenceOverrideCommand;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @PatchMapping("/{recurrenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long recurrenceId,
            @Valid @RequestBody UpdatePersonalRecurrenceGroupShareRequest request
    ) {
        shareService.update(
                account.accountId(),
                groupSpaceId,
                recurrenceId,
                new UpdatePersonalRecurrenceGroupShareCommand(
                        request.showOriginalDetails(),
                        request.overrideTitle(),
                        request.overrideStartAt(),
                        request.overrideEndAt(),
                        request.overrideAllDay()
                )
        );
    }

    @PatchMapping("/{recurrenceId}/occurrence-overrides")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateOccurrence(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long recurrenceId,
            @Valid @RequestBody UpdatePersonalRecurrenceGroupShareOccurrenceOverrideRequest request
    ) {
        shareService.updateOccurrence(
                account.accountId(),
                groupSpaceId,
                recurrenceId,
                new UpdatePersonalRecurrenceGroupShareOccurrenceOverrideCommand(
                        request.originStartAt(),
                        request.overrideTitle(),
                        request.overrideStartAt(),
                        request.overrideEndAt(),
                        request.overrideAllDay()
                )
        );
    }

    @DeleteMapping("/{recurrenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long groupSpaceId,
            @PathVariable Long recurrenceId
    ) {
        shareService.remove(account.accountId(), groupSpaceId, recurrenceId);
    }
}
