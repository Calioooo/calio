package com.calio.calendar.integration.connection.controller;

import com.calio.calendar.integration.connection.controller.dto.GoogleCalendarConnectRequest;
import com.calio.calendar.integration.connection.controller.dto.GoogleCalendarIntegrationResponse;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
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

    private final GoogleCalendarConnectionService googleCalendarConnectionService;
    private final GoogleOperationJobEnqueueService operationJobEnqueueService;

    public GoogleCalendarIntegrationController(
            GoogleCalendarConnectionService googleCalendarConnectionService,
            GoogleOperationJobEnqueueService operationJobEnqueueService
    ) {
        this.googleCalendarConnectionService = googleCalendarConnectionService;
        this.operationJobEnqueueService = operationJobEnqueueService;
    }

    @PostMapping
    public GoogleCalendarIntegrationResponse connect(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody GoogleCalendarConnectRequest request
    ) {
        return googleCalendarConnectionService.connect(
                account.accountId(),
                request.authorizationCode()
        );
    }

    @GetMapping
    public GoogleCalendarIntegrationResponse getConnectionStatus(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return googleCalendarConnectionService.getConnectionStatus(account.accountId());
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        googleCalendarConnectionService.disconnect(account.accountId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> sync(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        operationJobEnqueueService.enqueueManualSync(account.accountId());
        return ResponseEntity.accepted().build();
    }
}
