package com.calio.calendar.notification.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpApnsGatewayTest {

    private final HttpClient client = mock(HttpClient.class);

    @AfterEach
    void clearInterruptedState() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("APNs 성공 응답은 provider request ID와 함께 accepted로 분류한다")
    void givenSuccessfulResponse_whenSend_thenReturnsAccepted() throws Exception {
        stubResponse(200, "{}", "request-id");

        ApnsSendResult result = gateway().send(message());

        assertThat(result.type()).isEqualTo(ApnsSendResultType.ACCEPTED);
        assertThat(result.requestId()).isEqualTo("request-id");
    }

    @Test
    @DisplayName("APNs invalid token 응답은 invalid endpoint로 분류한다")
    void givenInvalidTokenResponse_whenSend_thenReturnsInvalidEndpoint() throws Exception {
        stubResponse(410, "{\"reason\":\"Unregistered\"}", "request-id");

        ApnsSendResult result = gateway().send(message());

        assertThat(result.type()).isEqualTo(ApnsSendResultType.INVALID_ENDPOINT);
        assertThat(result.requestId()).isEqualTo("request-id");
    }

    @Test
    @DisplayName("APNs rate limit과 server error는 transient failure로 분류한다")
    void givenTransientResponse_whenSend_thenReturnsTransientFailure() throws Exception {
        stubResponse(429, "{\"reason\":\"TooManyRequests\"}", null);

        ApnsSendResult result = gateway().send(message());

        assertThat(result.type()).isEqualTo(ApnsSendResultType.TRANSIENT_FAILURE);
    }

    @Test
    @DisplayName("APNs 전송이 interrupt되면 thread interrupt 상태를 복원하고 transient failure를 반환한다")
    void givenInterruptedSend_whenSend_thenRestoresInterruptedState() throws Exception {
        when(client.send(any(), any())).thenThrow(new InterruptedException());

        ApnsSendResult result = gateway().send(message());

        assertThat(result.type()).isEqualTo(ApnsSendResultType.TRANSIENT_FAILURE);
        assertThat(result.reason()).isEqualTo("interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @DisplayName("APNs credential이 없으면 network 호출 없이 configuration failure를 반환한다")
    void givenMissingConfiguration_whenSend_thenSkipsNetworkCall() {
        HttpApnsGateway gateway = new HttpApnsGateway(
                new ApnsProperties("development", "", "", "", ""),
                client
        );

        ApnsSendResult result = gateway.send(message());

        assertThat(result.type()).isEqualTo(ApnsSendResultType.CONFIGURATION_FAILURE);
        verifyNoInteractions(client);
    }

    private HttpApnsGateway gateway() {
        return new HttpApnsGateway(
                new ApnsProperties("development", "team", "key", "bundle", privateKey()),
                client
        );
    }

    private ApnsMessage message() {
        return new ApnsMessage("token", "{}", Instant.parse("2026-09-08T00:05:00Z"));
    }

    private void stubResponse(int statusCode, String body, String requestId) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeadersFactory.withRequestId(requestId));
        when(client.<String>send(any(), any())).thenReturn(response);
    }

    private String privateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            ECPrivateKey privateKey = (ECPrivateKey) generator.generateKeyPair().getPrivate();
            return "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getEncoder().encodeToString(privateKey.getEncoded())
                    + "\n-----END PRIVATE KEY-----";
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static class HttpHeadersFactory {

        private static HttpHeaders withRequestId(String requestId) {
            return HttpHeaders.of(
                    requestId == null ? Map.of() : Map.of("apns-id", List.of(requestId)),
                    (name, value) -> true
            );
        }
    }
}
