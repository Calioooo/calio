package com.calio.calendar.integration.sync;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.GoogleCalendarUnauthorizedException;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.sync.GoogleCalendarSyncMode;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarEventRequestService {

    private final GoogleCalendarEventsClient eventsClient;
    private final GoogleCalendarAccessTokenService accessTokenService;

    public GoogleCalendarEventRequestService(
            GoogleCalendarEventsClient eventsClient,
            GoogleCalendarAccessTokenService accessTokenService
    ) {
        this.eventsClient = eventsClient;
        this.accessTokenService = accessTokenService;
    }

    public GoogleCalendarEventPage listEvents(
            Long integrationId,
            GoogleCalendarSyncMode mode,
            String nextSyncToken,
            String pageToken,
            GoogleCalendarSyncRunContext context
    ) {
        try {
            return listEvents(mode, nextSyncToken, pageToken, context.accessToken());
        } catch (GoogleCalendarUnauthorizedException exception) {
            String refreshedAccessToken = refreshAccessToken(integrationId, context);
            try {
                return listEvents(mode, nextSyncToken, pageToken, refreshedAccessToken);
            } catch (GoogleCalendarUnauthorizedException retryException) {
                throw reconnectRequired(retryException);
            }
        }
    }

    public Optional<GoogleCalendarEventResponse> getEvent(
            Long integrationId,
            String externalEventId,
            GoogleCalendarSyncRunContext context
    ) {
        try {
            return eventsClient.getEvent(context.accessToken(), externalEventId);
        } catch (GoogleCalendarUnauthorizedException exception) {
            String refreshedAccessToken = refreshAccessToken(integrationId, context);
            try {
                return eventsClient.getEvent(refreshedAccessToken, externalEventId);
            } catch (GoogleCalendarUnauthorizedException retryException) {
                throw reconnectRequired(retryException);
            }
        }
    }

    private GoogleCalendarEventPage listEvents(
            GoogleCalendarSyncMode mode,
            String nextSyncToken,
            String pageToken,
            String accessToken
    ) {
        return eventsClient.listEvents(
                accessToken,
                mode,
                mode == GoogleCalendarSyncMode.INCREMENTAL ? nextSyncToken : null,
                pageToken
        );
    }

    private String refreshAccessToken(
            Long integrationId,
            GoogleCalendarSyncRunContext context
    ) {
        String refreshedAccessToken = accessTokenService.forceRefresh(integrationId);
        context.replaceAccessToken(refreshedAccessToken);
        return refreshedAccessToken;
    }

    private CalioException reconnectRequired(GoogleCalendarUnauthorizedException cause) {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED, cause);
    }
}
