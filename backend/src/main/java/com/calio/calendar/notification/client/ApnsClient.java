package com.calio.calendar.notification.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApnsClient {

  private static final String DEVICE_URI_TEMPLATE = "/3/device/{token}";
  private final ApnsProperties properties;
  private final ApnsProviderTokenProvider providerTokenProvider;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public ApnsClient(
      ApnsProperties properties,
      ApnsProviderTokenProvider providerTokenProvider,
      @Qualifier("apnsRestClient") RestClient restClient,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.providerTokenProvider = providerTokenProvider;
    this.restClient = restClient;
    this.objectMapper = objectMapper;
  }

  public ApnsSendResult send(ApnsMessage message) {
    String providerToken;
    try {
      providerToken = providerTokenProvider.getProviderToken();
    } catch (ApnsProviderTokenException exception) {
      return ApnsSendResult.configurationFailure(exception.getMessage());
    }

    try {
      ResponseEntity<String> response =
          restClient
              .post()
              .uri(DEVICE_URI_TEMPLATE, message.token())
              .headers(headers -> applyHeaders(headers, message, providerToken))
              .contentType(MediaType.APPLICATION_JSON)
              .body(payload(message))
              .retrieve()
              .toEntity(String.class);
      return classify(
          response.getStatusCode(), response.getHeaders().getFirst("apns-id"), response.getBody());
    } catch (RestClientResponseException exception) {
      HttpHeaders headers = exception.getResponseHeaders();
      return classify(
          exception.getStatusCode(),
          headers == null ? null : headers.getFirst("apns-id"),
          exception.getResponseBodyAsString());
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

  private void applyHeaders(HttpHeaders headers, ApnsMessage message, String providerToken) {
    headers.setBearerAuth(providerToken);
    headers.set("apns-topic", properties.bundleId());
    headers.set("apns-push-type", "alert");
    headers.set("apns-expiration", Long.toString(message.expiration().getEpochSecond()));
  }

  private String payload(ApnsMessage message) {
    try {
      return objectMapper.writeValueAsString(
          java.util.Map.of(
              "aps",
              java.util.Map.of(
                  "alert",
                  java.util.Map.of("title", message.title(), "body", message.body()),
                  "sound",
                  "default"),
              "calio",
              message.metadata()));
    } catch (JacksonException exception) {
      throw new IllegalStateException("Cannot serialize APNs payload.", exception);
    }
  }

  private ApnsSendResult classify(HttpStatusCode statusCode, String requestId, String reason) {
    if (statusCode.is2xxSuccessful()) {
      return new ApnsSendResult(ApnsSendResultType.ACCEPTED, requestId, null);
    }
    if (statusCode == HttpStatus.GONE
        || contains(reason, "BadDeviceToken")
        || contains(reason, "Unregistered")) {
      return new ApnsSendResult(ApnsSendResultType.INVALID_ENDPOINT, requestId, reason);
    }
    if (statusCode.is5xxServerError() || statusCode == HttpStatus.TOO_MANY_REQUESTS) {
      return new ApnsSendResult(ApnsSendResultType.TRANSIENT_FAILURE, requestId, reason);
    }
    return new ApnsSendResult(ApnsSendResultType.REJECTED, requestId, reason);
  }

  private boolean contains(String value, String text) {
    return value != null && value.contains(text);
  }

  private ApnsSendResult transientFailure(Exception exception) {
    return new ApnsSendResult(
        ApnsSendResultType.TRANSIENT_FAILURE, null, exception.getClass().getSimpleName());
  }
}
