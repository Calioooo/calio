package com.calio.calendar.integration.controller;

import com.calio.calendar.integration.controller.dto.GoogleCalendarConnectRequest;
import com.calio.calendar.integration.controller.dto.GoogleCalendarIntegrationResponse;
import com.calio.calendar.integration.service.GoogleCalendarIntegrationService;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/integrations/google-calendar")
public class GoogleCalendarIntegrationController {

    private final GoogleCalendarIntegrationService googleCalendarIntegrationService;

    public GoogleCalendarIntegrationController(GoogleCalendarIntegrationService googleCalendarIntegrationService) {
        this.googleCalendarIntegrationService = googleCalendarIntegrationService;
    }

    @PostMapping
    public GoogleCalendarIntegrationResponse connect(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody GoogleCalendarConnectRequest request
    ) {
        return googleCalendarIntegrationService.connect(account.accountId(), request);
    }

    @GetMapping
    public GoogleCalendarIntegrationResponse getConnectionStatus(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return googleCalendarIntegrationService.getConnectionStatus(account.accountId());
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        googleCalendarIntegrationService.disconnect(account.accountId());
        return ResponseEntity.noContent().build();
    }
}
