package com.calio.calendar.integration.connection.service;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoogleCalendarIntegrationQueryService {
    private final GoogleCalendarIntegrationRepository integrationRepository;

    public GoogleCalendarIntegrationQueryService(GoogleCalendarIntegrationRepository integrationRepository) {
        this.integrationRepository = integrationRepository;
    }

    public Optional<GoogleCalendarIntegration> findIntegration(Long accountId) {
        return integrationRepository.findByAccountId(accountId);
    }

    public boolean hasIntegration(Long accountId) {
        return integrationRepository.existsByAccountId(accountId);
    }
}
