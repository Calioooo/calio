package com.calio.calendar.external.google;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.external.google.dto.GoogleCalendarEventWriteRequest;
import com.calio.calendar.integration.sync.GoogleCalendarSyncMode;
import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class GoogleCalendarEventsClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarEventsClient.class);
    private static final String EVENT_FIELDS = "id,status,etag,updated,summary,description,"
            + "recurrence,recurringEventId,originalStartTime(date,dateTime,timeZone),"
            + "start(date,dateTime,timeZone),end(date,dateTime,timeZone)";
    private static final String PARTIAL_FIELDS = "nextPageToken,nextSyncToken,timeZone,"
            + "items(" + EVENT_FIELDS + ")";
    private static final String INSUFFICIENT_PERMISSIONS_REASON = "insufficientPermissions";

    private final GoogleOAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GoogleCalendarEventsClient(
            GoogleOAuthProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("googleCalendarEventsRestClient") RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public GoogleCalendarEventPage listEvents(
            String accessToken,
            GoogleCalendarSyncMode mode,
            String syncToken,
            String pageToken
    ) {
        validateQueryContract(mode, syncToken, pageToken);
        try {
            return requestPage(accessToken, mode, syncToken, pageToken);
        } catch (RestClientResponseException exception) {
            throw translateResponseFailure(exception);
        } catch (CalioException exception) {
            throw exception;
        } catch (RestClientException exception) {
            if (isDeserializationFailure(exception)) {
                throw invalidResponse(exception);
            }
            throw syncFailed(exception);
        }
    }

    public Optional<GoogleCalendarEventResponse> getEvent(
            String accessToken,
            String externalEventId
    ) {
        validateExternalEventId(externalEventId);
        try {
            GoogleCalendarEventResponse response = requestEvent(accessToken, externalEventId);
            return Optional.of(response);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw translateEventResponseFailure(exception);
        } catch (CalioException exception) {
            throw exception;
        } catch (RestClientException exception) {
            if (isDeserializationFailure(exception)) {
                throw invalidResponse(exception);
            }
            throw syncFailed(exception);
        }
    }

    public GoogleCalendarEventResponse insertEvent(
            String accessToken, GoogleCalendarEventWriteRequest request) {
        if (request == null || !hasText(request.id())) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }
        try {
            return requireRequestedEventId(writeEvent(accessToken, null, request, false), request.id());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw translateEventResponseFailure(exception);
            }
            GoogleCalendarEventResponse existing = getEvent(accessToken, request.id())
                    .orElseThrow(() -> syncFailed(exception));
            return requireRequestedEventId(existing, request.id());
        }
    }

    public GoogleCalendarEventResponse patchEvent(
            String accessToken, String externalEventId, GoogleCalendarEventWriteRequest request) {
        try {
            return writeEvent(accessToken, externalEventId, request, true);
        } catch (RestClientResponseException exception) {
            throw translateEventResponseFailure(exception);
        }
    }

    public boolean deleteEvent(String accessToken, String externalEventId) {
        validateExternalEventId(externalEventId);
        try {
            restClient.delete().uri(writeEventUri(externalEventId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return false;
            }
            throw translateEventResponseFailure(exception);
        } catch (RestClientException exception) {
            throw syncFailed(exception);
        }
    }

    private GoogleCalendarEventResponse writeEvent(
            String accessToken,
            String externalEventId,
            GoogleCalendarEventWriteRequest request,
            boolean patch
    ) {
        if (request == null || (patch && !hasText(externalEventId))) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }
        try {
            var specification = patch
                    ? restClient.patch().uri(writeEventUri(externalEventId))
                    : restClient.post().uri(writeEventUri(null));
            GoogleCalendarEventResponse response = specification
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .body(request).retrieve().body(GoogleCalendarEventResponse.class);
            if (response == null) {
                throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw exception;
        } catch (CalioException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw syncFailed(exception);
        }
    }

    private GoogleCalendarEventResponse requireRequestedEventId(
            GoogleCalendarEventResponse response,
            String providerIdentity
    ) {
        if (!providerIdentity.equals(response.id())) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        return response;
    }

    private GoogleCalendarEventResponse requestEvent(String accessToken, String externalEventId) {
        GoogleCalendarEventResponse response = restClient.get()
                .uri(eventUri(externalEventId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GoogleCalendarEventResponse.class);
        if (response == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        return response;
    }

    private GoogleCalendarEventPage requestPage(
            String accessToken,
            GoogleCalendarSyncMode mode,
            String syncToken,
            String pageToken
    ) {
        GoogleCalendarEventPage response = restClient.get()
                .uri(eventsUri(mode, syncToken, pageToken))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GoogleCalendarEventPage.class);
        if (response == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        return response;
    }

    private URI eventsUri(
            GoogleCalendarSyncMode mode,
            String syncToken,
            String pageToken
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getCalendarEventsUrl())
                .queryParam("singleEvents", false)
                .queryParam("showDeleted", true)
                .queryParam("maxResults", 2500)
                .queryParam("fields", PARTIAL_FIELDS);
        if (mode == GoogleCalendarSyncMode.INCREMENTAL) {
            builder.queryParam("syncToken", syncToken);
        }
        if (pageToken != null) {
            builder.queryParam("pageToken", pageToken);
        }
        return builder.build().encode().toUri();
    }

    private URI eventUri(String externalEventId) {
        return UriComponentsBuilder
                .fromUriString(properties.getCalendarEventsUrl())
                .pathSegment(externalEventId)
                .queryParam("fields", EVENT_FIELDS)
                .build()
                .encode()
                .toUri();
    }

    private URI writeEventUri(String externalEventId) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.getCalendarEventsUrl());
        if (externalEventId != null) {
            builder.pathSegment(externalEventId);
        }
        return builder.queryParam("sendUpdates", "none")
                .queryParam("fields", EVENT_FIELDS).build().encode().toUri();
    }

    private void validateExternalEventId(String externalEventId) {
        if (!hasText(externalEventId)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }
    }

    private RuntimeException translateResponseFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return new GoogleCalendarUnauthorizedException(exception);
        }
        if (status == HttpStatus.GONE.value()) {
            return new GoogleCalendarSyncTokenExpiredException(exception);
        }
        if (isScopeFailure(exception)) {
            logFailure(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED, status, exception);
            return new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED, exception);
        }
        return syncFailed(exception);
    }

    private RuntimeException translateEventResponseFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return new GoogleCalendarUnauthorizedException(exception);
        }
        if (isScopeFailure(exception)) {
            logFailure(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED, status, exception);
            return new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED, exception);
        }
        return syncFailed(exception);
    }

    private boolean isScopeFailure(RestClientResponseException exception) {
        if (exception.getStatusCode().value() != HttpStatus.FORBIDDEN.value()) {
            return false;
        }
        try {
            var errors = objectMapper.readTree(exception.getResponseBodyAsString())
                    .get("error")
                    .get("errors");
            if (errors == null || !errors.isArray()) {
                return false;
            }
            for (var error : errors) {
                var reason = error.get("reason");
                if (reason != null && INSUFFICIENT_PERMISSIONS_REASON.equals(reason.asString())) {
                    return true;
                }
            }
            return false;
        } catch (JacksonException | NullPointerException ignored) {
            return false;
        }
    }

    private CalioException invalidResponse(Exception exception) {
        logFailure(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID, null, exception);
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID, exception);
    }

    private CalioException syncFailed(Exception exception) {
        logFailure(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED, httpStatus(exception), exception);
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED, exception);
    }

    private void validateQueryContract(
            GoogleCalendarSyncMode mode,
            String syncToken,
            String pageToken
    ) {
        boolean isIncrementalWithoutCursor =
                mode == GoogleCalendarSyncMode.INCREMENTAL && !hasText(syncToken);
        boolean isBlankPageToken = pageToken != null && pageToken.isBlank();
        if (mode == null || isIncrementalWithoutCursor || isBlankPageToken) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isDeserializationFailure(RestClientException exception) {
        return exception.contains(HttpMessageNotReadableException.class);
    }

    private void logFailure(
            ErrorCode errorCode,
            Integer status,
            Exception exception
    ) {
        log.warn(
                "Google Calendar Events request failed. errorCode={} httpStatus={} causeType={}",
                errorCode.name(),
                status,
                exception.getClass().getSimpleName()
        );
    }

    private Integer httpStatus(Exception exception) {
        return exception instanceof RestClientResponseException responseException
                ? responseException.getStatusCode().value()
                : null;
    }

}
