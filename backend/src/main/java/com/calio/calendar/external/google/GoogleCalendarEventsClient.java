package com.calio.calendar.external.google;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTimeResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventWriteRequest;
import com.calio.calendar.integration.sync.GoogleCalendarSyncMode;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
            return restClient.get()
                    .uri(eventsUri(mode, syncToken, pageToken))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleCalendarEventPage.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.GONE) {
                throw new GoogleCalendarSyncTokenExpiredException(exception);
            }
            throw translateEventResponseFailure(exception);
        } catch (RestClientException exception) {
            throw handleRestClientException(exception);
        }
    }

    public Optional<GoogleCalendarEventResponse> getEvent(
            String accessToken,
            String externalEventId
    ) {
        if (!hasText(externalEventId)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }

        try {
            GoogleCalendarEventResponse response = restClient.get()
                    .uri(eventUri(externalEventId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleCalendarEventResponse.class);
            return Optional.of(response);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw translateEventResponseFailure(exception);
        } catch (RestClientException exception) {
            throw handleRestClientException(exception);
        }
    }

    public Optional<GoogleCalendarEventResponse> getRecurrenceOccurrence(
            String accessToken, String externalRecurrenceId, Instant originStartAt
    ) {
        if (!hasText(externalRecurrenceId) || originStartAt == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }

        try {
            GoogleCalendarEventPage page = restClient.get()
                    .uri(occurrenceUri(externalRecurrenceId, originStartAt))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(GoogleCalendarEventPage.class);
            if (page == null || page.items() == null) {
                throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
            }
            return page.items().stream().filter(event -> event.originalStartTime() != null)
                    .filter(event -> matchesOrigin(event.originalStartTime(), originStartAt))
                    .findFirst();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                    || exception.getStatusCode().value() == HttpStatus.GONE.value()) {
                return Optional.empty();
            }
            throw translateEventResponseFailure(exception);
        } catch (RestClientException exception) {
            throw handleRestClientException(exception);
        }
    }

    public GoogleCalendarEventResponse post(
            String accessToken,
            GoogleCalendarEventWriteRequest request
    ) {
        if (request == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }

        try {
            return restClient.post()
                    .uri(createEventUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .body(request).retrieve().body(GoogleCalendarEventResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw translateEventResponseFailure(exception);
            }
            return getEvent(accessToken, request.id())
                    .orElseThrow(() -> handleRestClientException(exception));
        } catch (RestClientException exception) {
            throw handleRestClientException(exception);
        }
    }

    public GoogleCalendarEventResponse patch(
            String accessToken,
            String externalEventId,
            String expectedProviderEtag,
            GoogleCalendarEventWriteRequest request
    ) {
        if (!hasText(externalEventId) || !hasText(expectedProviderEtag) || request == null) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }
        try {
            return restClient.patch()
                    .uri(existingEventWriteUri(externalEventId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.IF_MATCH, expectedProviderEtag)
                    .body(request).retrieve().body(GoogleCalendarEventResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.PRECONDITION_FAILED.value()) {
                throw new GoogleCalendarEventVersionConflictException(exception);
            }
            throw translateEventResponseFailure(exception);
        } catch (RestClientException exception) {
            throw handleRestClientException(exception);
        }
    }

    public boolean delete(
            String accessToken,
            String externalEventId,
            String expectedProviderEtag
    ) {
        if (!hasText(externalEventId) || !hasText(expectedProviderEtag)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
        }
        try {
            restClient.delete().uri(existingEventWriteUri(externalEventId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.IF_MATCH, expectedProviderEtag)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.GONE.value()) {
                return false;
            }
            if (status == HttpStatus.PRECONDITION_FAILED.value()) {
                throw new GoogleCalendarEventVersionConflictException(exception);
            }
            throw translateEventResponseFailure(exception);
        } catch (RestClientException exception) {
            throw handleRestClientException(exception);
        }
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

    private URI createEventUri() {
        return eventWriteUriBuilder().build().encode().toUri();
    }

    private URI existingEventWriteUri(String externalEventId) {
        return eventWriteUriBuilder()
                .pathSegment(externalEventId)
                .build()
                .encode()
                .toUri();
    }

    private URI occurrenceUri(String externalRecurrenceEventId, Instant originStartAt) {
        return UriComponentsBuilder.fromUriString(properties.getCalendarEventsUrl())
                .pathSegment(externalRecurrenceEventId, "instances")
                .queryParam("originalStart", originStartAt.toString())
                .queryParam("showDeleted", true)
                .queryParam("maxResults", 2)
                .queryParam("fields", PARTIAL_FIELDS)
                .build().encode().toUri();
    }

    private UriComponentsBuilder eventWriteUriBuilder() {
        return UriComponentsBuilder
                .fromUriString(properties.getCalendarEventsUrl())
                .queryParam("sendUpdates", "none")
                .queryParam("fields", EVENT_FIELDS);
    }

    private RuntimeException translateEventResponseFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return new GoogleCalendarUnauthorizedException(exception);
        }
        if (isInsufficientPermissionsFailure(exception)) {
            logFailure(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED, status, exception);
            return new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED, exception);
        }

        logFailure(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED, httpStatus(exception), exception);
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED, exception);
    }

    private boolean isInsufficientPermissionsFailure(RestClientResponseException exception) {
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

    private CalioException handleRestClientException(RestClientException exception) {
        if (exception.contains(HttpMessageNotReadableException.class)) {
            logFailure(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID, null, exception);
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID, exception);
        }

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

    private boolean matchesOrigin(
            GoogleCalendarEventTimeResponse value,
            Instant originStartAt
    ) {
        if (hasText(value.dateTime())) {
            return originStartAt.equals(OffsetDateTime.parse(value.dateTime()).toInstant());
        }
        return hasText(value.date())
                && originStartAt.atOffset(ZoneOffset.UTC).toLocalDate().toString().equals(value.date());
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
