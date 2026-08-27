package com.calio.calendar.integration.connection.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.repository.GoogleCalendarConnectionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoogleCalendarConnectionQueryService {
    private final GoogleCalendarConnectionRepository connectionRepository;

    public GoogleCalendarConnectionQueryService(GoogleCalendarConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    public GoogleCalendarConnection getConnectedConnection(Long accountId) {
        return connectionRepository.findConnectedByAccountId(accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
    }

    public Optional<GoogleCalendarConnection> findConnectedConnection(Long accountId) {
        return connectionRepository.findConnectedByAccountId(accountId);
    }

    public GoogleCalendarConnection getConnectedConnectionById(Long connectionId) {
        return connectionRepository.findByIdWithIntegration(connectionId)
                .filter(GoogleCalendarConnection::isConnected)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
    }

    public List<Long> listConnectedAccountIds(Long afterAccountId, int limit) {
        return connectionRepository.findConnectedAccountIdsAfter(afterAccountId, PageRequest.of(0, limit));
    }
}
