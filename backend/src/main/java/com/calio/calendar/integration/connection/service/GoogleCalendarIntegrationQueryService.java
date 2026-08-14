package com.calio.calendar.integration.connection.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoogleCalendarIntegrationQueryService {

    private final GoogleCalendarIntegrationRepository integrationRepository;

    public GoogleCalendarIntegrationQueryService(
            GoogleCalendarIntegrationRepository integrationRepository
    ) {
        this.integrationRepository = integrationRepository;
    }

    public GoogleCalendarIntegration getIntegration(Long accountId) {
        return integrationRepository.findByAccountId(accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
    }

    public GoogleCalendarIntegration getIntegrationById(Long integrationId) {
        return integrationRepository.findById(integrationId)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
    }

    public Optional<GoogleCalendarIntegration> getIntegrationIfExists(Long accountId) {
        return integrationRepository.findByAccountId(accountId);
    }

    public boolean hasIntegration(Long accountId) {
        return integrationRepository.existsByAccountId(accountId);
    }

    public List<Long> listConnectedAccountIds(Long afterAccountId, int limit) {
        return integrationRepository.findConnectedAccountIdsAfter(
                afterAccountId,
                PageRequest.of(0, limit)
        );
    }
}
