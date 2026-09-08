package com.calio.calendar.integration.sync;

import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.service.GoogleCalendarAccessTokenService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarRecurrenceDeleteReconciliationService {

    private final GoogleCalendarRecurrenceMappingQueryService mappingQueryService;
    private final GoogleCalendarRecurrenceMappingCommandService mappingCommandService;
    private final GoogleCalendarAccessTokenService accessTokenService;
    private final GoogleCalendarEventsClient eventsClient;

    public GoogleCalendarRecurrenceDeleteReconciliationService(
            GoogleCalendarRecurrenceMappingQueryService mappingQueryService,
            GoogleCalendarRecurrenceMappingCommandService mappingCommandService,
            GoogleCalendarAccessTokenService accessTokenService,
            GoogleCalendarEventsClient eventsClient
    ) {
        this.mappingQueryService = mappingQueryService;
        this.mappingCommandService = mappingCommandService;
        this.accessTokenService = accessTokenService;
        this.eventsClient = eventsClient;
    }

    public void reconcilePendingDeletes(GoogleCalendarConnection connection) {
        List<GoogleCalendarRecurrenceEventMapping> pendingMappings =
                mappingQueryService.listRecurrenceEventMappings(connection.getId()).stream()
                        .filter(GoogleCalendarRecurrenceEventMapping::isProviderDeletePending)
                        .toList();
        if (pendingMappings.isEmpty()) {
            return;
        }
        String accessToken = accessTokenService.getAccessToken(connection.getId());
        pendingMappings.forEach(mapping -> deleteProviderAggregate(accessToken, mapping));
    }

    private void deleteProviderAggregate(
            String accessToken,
            GoogleCalendarRecurrenceEventMapping mapping
    ) {
        GoogleCalendarEventResponse providerEvent = eventsClient
                .getEvent(accessToken, mapping.getExternalEventId())
                .orElse(null);
        if (providerEvent != null) {
            eventsClient.deleteEvent(accessToken, mapping.getExternalEventId(), providerEvent.etag());
        }
        mappingCommandService.deleteRecurrenceAggregateMappings(mapping);
    }
}
