package com.calio.calendar.external.google;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final String PARTIAL_FIELDS = "nextPageToken,nextSyncToken,timeZone,"
            + "items(id,status,etag,updated,summary,description,recurrence,recurringEventId,"
            + "start(date,dateTime,timeZone),end(date,dateTime,timeZone))";
    private static final String INSUFFICIENT_PERMISSIONS_REASON = "insufficientPermissions";

    private final GoogleOAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public GoogleCalendarEventsClient(
            GoogleOAuthProperties properties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder
    ) {
        this(
                properties,
                objectMapper,
                createRestClient(restClientBuilder)
        );
    }

    GoogleCalendarEventsClient(
            GoogleOAuthProperties properties,
            ObjectMapper objectMapper,
            RestClient restClient
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
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
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

    private static RestClient createRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.requestFactory(createRequestFactory()).build();
    }

    private static SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }
}
