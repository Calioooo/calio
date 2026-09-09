package com.calio.calendar.notification.client;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ApnsClient {

    private static final String DEVICE_URI_TEMPLATE = "/3/device/{token}";
    private final ApnsProperties properties;
    private final ApnsProviderTokenProvider providerTokenProvider;
    private final RestClient restClient;

    public ApnsClient(
            ApnsProperties properties,
            ApnsProviderTokenProvider providerTokenProvider,
            @Qualifier("apnsRestClient") RestClient restClient
    ) {
        this.properties = properties;
        this.providerTokenProvider = providerTokenProvider;
        this.restClient = restClient;
    }

    public ApnsSendResult send(ApnsMessage message) {
        String providerToken;
        try {
            providerToken = providerTokenProvider.getProviderToken();
        } catch (ApnsProviderTokenException exception) {
            return ApnsSendResult.configurationFailure(exception.getMessage());
        }

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(DEVICE_URI_TEMPLATE, message.token())
                    .headers(headers -> applyHeaders(headers, message, providerToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message.payload())
                    .retrieve()
                    .toEntity(String.class);
            return classify(
                    response.getStatusCode().value(),
                    response.getHeaders().getFirst("apns-id"),
                    response.getBody()
            );
        } catch (RestClientResponseException exception) {
            HttpHeaders headers = exception.getResponseHeaders();
            return classify(
                    exception.getStatusCode().value(),
                    headers == null ? null : headers.getFirst("apns-id"),
                    exception.getResponseBodyAsString()
            );
        } catch (RestClientException exception) {
            if (exception.contains(InterruptedException.class)) {
                Thread.currentThread().interrupt();
                return new ApnsSendResult(ApnsSendResultType.TRANSIENT_FAILURE, null, "interrupted");
            }
            return transientFailure(exception);
        } catch (Exception exception) {
            return transientFailure(exception);
        }
    }

    private void applyHeaders(
            HttpHeaders headers,
            ApnsMessage message,
            String providerToken
    ) {
        headers.setBearerAuth(providerToken);
        headers.set("apns-topic", properties.bundleId());
        headers.set("apns-push-type", "alert");
        headers.set("apns-expiration", Long.toString(message.expiration().getEpochSecond()));
    }

    private ApnsSendResult classify(int statusCode, String requestId, String reason) {
        if (statusCode >= 200 && statusCode < 300) {
            return new ApnsSendResult(ApnsSendResultType.ACCEPTED, requestId, null);
        }
        if (statusCode == 410
                || contains(reason, "BadDeviceToken")
                || contains(reason, "Unregistered")) {
            return new ApnsSendResult(ApnsSendResultType.INVALID_ENDPOINT, requestId, reason);
        }
        if (statusCode >= 500 || statusCode == 429) {
            return new ApnsSendResult(ApnsSendResultType.TRANSIENT_FAILURE, requestId, reason);
        }
        return new ApnsSendResult(ApnsSendResultType.REJECTED, requestId, reason);
    }

    private boolean contains(String value, String text) {
        return value != null && value.contains(text);
    }

    private ApnsSendResult transientFailure(Exception exception) {
        return new ApnsSendResult(
                ApnsSendResultType.TRANSIENT_FAILURE,
                null,
                exception.getClass().getSimpleName()
        );
    }

}
