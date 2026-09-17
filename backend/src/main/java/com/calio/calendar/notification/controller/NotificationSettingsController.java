package com.calio.calendar.notification.controller;

import com.calio.calendar.notification.controller.dto.NotificationSettingsResponse;
import com.calio.calendar.notification.controller.dto.UpdateNotificationSettingsRequest;
import com.calio.calendar.notification.usecase.GetNotificationSettingsUseCase;
import com.calio.calendar.notification.usecase.UpdateNotificationSettingsUseCase;
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

  private final GetNotificationSettingsUseCase getNotificationSettingsUseCase;
  private final UpdateNotificationSettingsUseCase updateNotificationSettingsUseCase;

  public NotificationSettingsController(
      GetNotificationSettingsUseCase getNotificationSettingsUseCase,
      UpdateNotificationSettingsUseCase updateNotificationSettingsUseCase) {
    this.getNotificationSettingsUseCase = getNotificationSettingsUseCase;
    this.updateNotificationSettingsUseCase = updateNotificationSettingsUseCase;
  }

  @GetMapping
  public NotificationSettingsResponse get(@AuthenticationPrincipal AuthenticatedAccount account) {
    return NotificationSettingsResponse.from(
        getNotificationSettingsUseCase.execute(account.accountId()));
  }

  @PutMapping
  public NotificationSettingsResponse update(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody UpdateNotificationSettingsRequest request) {
    return NotificationSettingsResponse.from(
        updateNotificationSettingsUseCase.execute(account.accountId(), request));
  }
}
