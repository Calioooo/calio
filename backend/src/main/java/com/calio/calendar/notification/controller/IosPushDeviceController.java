package com.calio.calendar.notification.controller;

import com.calio.calendar.notification.controller.dto.RegisterIosPushDeviceRequest;
import com.calio.calendar.notification.service.IosPushDeviceService;
import com.calio.calendar.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification-endpoints/ios")
public class IosPushDeviceController {

    private final IosPushDeviceService pushDeviceService;

    public IosPushDeviceController(IosPushDeviceService pushDeviceService) {
        this.pushDeviceService = pushDeviceService;
    }

    @PutMapping
    public ResponseEntity<Void> register(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody RegisterIosPushDeviceRequest request
    ) {
        pushDeviceService.register(
                account.accountId(),
                request.installationId(),
                request.apnsToken()
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{installationId}")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable String installationId
    ) {
        pushDeviceService.deactivate(account.accountId(), installationId);
        return ResponseEntity.noContent().build();
    }
}
