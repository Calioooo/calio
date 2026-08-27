package com.calio.calendar.integration.connection.service;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoogleCalendarIntegrationCommandService {
    private final GoogleCalendarIntegrationRepository integrationRepository;

    public GoogleCalendarIntegrationCommandService(GoogleCalendarIntegrationRepository integrationRepository) {
        this.integrationRepository = integrationRepository;
    }

    public Optional<GoogleCalendarIntegration> findIntegrationForUpdate(Long accountId) {
        return integrationRepository.findByAccountIdForUpdate(accountId);
    }

    public GoogleCalendarIntegration createIntegration(Long accountId) {
        return integrationRepository.saveAndFlush(new GoogleCalendarIntegration(accountId));
    }
}
