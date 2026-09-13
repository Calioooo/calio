package com.calio.calendar.notification.controller;

import com.calio.calendar.notification.controller.dto.NotificationSettingsResponse;
import com.calio.calendar.notification.controller.dto.UpdateNotificationSettingsRequest;
import com.calio.calendar.notification.service.AccountNotificationSettingsService;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification-settings")
public class NotificationSettingsController {

    private final AccountNotificationSettingsService settingsService;

    public NotificationSettingsController(AccountNotificationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public NotificationSettingsResponse get(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return NotificationSettingsResponse.from(settingsService.get(account.accountId()));
    }

    @PutMapping
    public NotificationSettingsResponse update(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody UpdateNotificationSettingsRequest request
    ) {
        return NotificationSettingsResponse.from(settingsService.update(account.accountId(), request));
    }
}
