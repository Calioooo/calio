package com.calio.calendar.integration.googlecalendar.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.googlecalendar.controller.dto.GoogleCalendarIntegrationResponse;
import com.calio.calendar.integration.googlecalendar.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.googlecalendar.repository.GoogleCalendarIntegrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarIntegrationPersistenceService {

    private final GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository;
    private final GoogleCalendarSyncTokenCleanupService syncTokenCleanupService;

    public GoogleCalendarIntegrationPersistenceService(
            GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository,
            GoogleCalendarSyncTokenCleanupService syncTokenCleanupService
    ) {
        this.googleCalendarIntegrationRepository = googleCalendarIntegrationRepository;
        this.syncTokenCleanupService = syncTokenCleanupService;
    }

    @Transactional
    public GoogleCalendarIntegrationResponse saveConnected(GoogleCalendarIntegration newIntegration) {
        GoogleCalendarIntegration integration = googleCalendarIntegrationRepository
                .findByAccountId(newIntegration.getAccountId())
                .map(existingIntegration -> replaceDisconnected(existingIntegration, newIntegration))
                .orElse(newIntegration);
        return GoogleCalendarIntegrationResponse.connected(
                googleCalendarIntegrationRepository.saveAndFlush(integration)
        );
    }

    @Transactional
    public void disconnect(Long accountId) {
        syncTokenCleanupService.deleteAllByAccountId(accountId);
        googleCalendarIntegrationRepository.deleteByAccountId(accountId);
    }

    private GoogleCalendarIntegration replaceDisconnected(
            GoogleCalendarIntegration existingIntegration,
            GoogleCalendarIntegration newIntegration
    ) {
        if (existingIntegration.isConnected()) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_INTEGRATION_ALREADY_CONNECTED);
        }

        existingIntegration.connect(
                newIntegration.getGoogleSubject(),
                newIntegration.getGoogleEmail(),
                newIntegration.getEncryptedRefreshToken(),
                newIntegration.getAccessToken(),
                newIntegration.getAccessTokenExpiresAt(),
                newIntegration.getConnectedAt()
        );
        return existingIntegration;
    }
}
