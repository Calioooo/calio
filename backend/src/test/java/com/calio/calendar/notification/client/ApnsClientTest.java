package com.calio.calendar.notification.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ApnsClientTest {

    private static final String APNS_HOST = "https://api.sandbox.push.apple.com";

    @Test
    @DisplayName("APNs 성공 응답은 provider request ID와 함께 accepted로 분류한다")
    void givenSuccessfulResponse_whenSend_thenReturnsAccepted() {
        // given
        TestClient testClient = testClient();
        testClient.server().expect(requestTo(APNS_HOST + "/3/device/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, startsWith("Bearer ")))
                .andExpect(header("apns-topic", "bundle"))
                .andExpect(header("apns-push-type", "alert"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON)
                        .header("apns-id", "request-id"));

        // when
        ApnsSendResult result = testClient.client().send(message());

        // then
        assertThat(result.type()).isEqualTo(ApnsSendResultType.ACCEPTED);
        assertThat(result.requestId()).isEqualTo("request-id");
        testClient.server().verify();
    }

    @Test
    @DisplayName("APNs 410 응답은 invalid endpoint로 분류한다")
    void givenGoneResponse_whenSend_thenReturnsInvalidEndpoint() {
        // given
        TestClient testClient = testClient();
        testClient.server().expect(requestTo(APNS_HOST + "/3/device/token"))
                .andRespond(withStatus(HttpStatus.GONE)
                        .body("{\"reason\":\"Unregistered\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        // when
        ApnsSendResult result = testClient.client().send(message());

        // then
        assertThat(result.type()).isEqualTo(ApnsSendResultType.INVALID_ENDPOINT);
        testClient.server().verify();
    }

    @Test
    @DisplayName("APNs BadDeviceToken 응답은 invalid endpoint로 분류한다")
    void givenBadDeviceTokenResponse_whenSend_thenReturnsInvalidEndpoint() {
        // given
        TestClient testClient = testClient();
        testClient.server().expect(requestTo(APNS_HOST + "/3/device/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"reason\":\"BadDeviceToken\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        // when
        ApnsSendResult result = testClient.client().send(message());

        // then
        assertThat(result.type()).isEqualTo(ApnsSendResultType.INVALID_ENDPOINT);
        testClient.server().verify();
    }

    @Test
    @DisplayName("APNs rate limit 응답은 transient failure로 분류한다")
    void givenRateLimitedResponse_whenSend_thenReturnsTransientFailure() {
        // given
        TestClient testClient = testClient();
        testClient.server().expect(requestTo(APNS_HOST + "/3/device/token"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // when
        ApnsSendResult result = testClient.client().send(message());

        // then
        assertThat(result.type()).isEqualTo(ApnsSendResultType.TRANSIENT_FAILURE);
        testClient.server().verify();
    }

    @Test
    @DisplayName("APNs server error 응답은 transient failure로 분류한다")
    void givenServerErrorResponse_whenSend_thenReturnsTransientFailure() {
        // given
        TestClient testClient = testClient();
        testClient.server().expect(requestTo(APNS_HOST + "/3/device/token"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // when
        ApnsSendResult result = testClient.client().send(message());

        // then
        assertThat(result.type()).isEqualTo(ApnsSendResultType.TRANSIENT_FAILURE);
        testClient.server().verify();
    }

    @Test
    @DisplayName("APNs terminal rejection은 rejected로 분류한다")
    void givenTerminalRejectionResponse_whenSend_thenReturnsRejected() {
        // given
        TestClient testClient = testClient();
        testClient.server().expect(requestTo(APNS_HOST + "/3/device/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"reason\":\"BadTopic\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        // when
        ApnsSendResult result = testClient.client().send(message());

        // then
        assertThat(result.type()).isEqualTo(ApnsSendResultType.REJECTED);
        testClient.server().verify();
    }

    @Test
    @DisplayName("APNs credential이 없으면 configuration failure를 반환한다")
    void givenMissingConfiguration_whenSend_thenReturnsConfigurationFailure() {
        // given
        ApnsClient client = apnsClient(
                new ApnsProperties("development", "", "", "", ""),
                RestClient.builder().build(),
                Clock.systemUTC()
        );

        // when
        ApnsSendResult result = client.send(message());

        // then
        assertThat(result.type()).isEqualTo(ApnsSendResultType.CONFIGURATION_FAILURE);
    }

    @Test
    @DisplayName("잘못된 APNs private key는 네트워크 요청 없이 configuration failure를 반환한다")
    void givenInvalidPrivateKey_whenSend_thenReturnsConfigurationFailureWithoutSendingRequest() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ApnsClient client = apnsClient(
                new ApnsProperties(
                        "development",
                        "team",
                        "key",
                        "bundle",
                        "-----BEGIN PRIVATE KEY-----\\ninvalid\\n-----END PRIVATE KEY-----"
                ),
                restClientBuilder.baseUrl(APNS_HOST).build(),
                Clock.systemUTC()
        );

        // when
        ApnsSendResult result = client.send(message());

        // then
        assertThat(result.type()).isEqualTo(ApnsSendResultType.CONFIGURATION_FAILURE);
        server.verify();
    }

    private TestClient testClient() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.baseUrl(APNS_HOST).build();
        return new TestClient(apnsClient(properties(), restClient, Clock.systemUTC()), server);
    }

    private ApnsClient apnsClient(ApnsProperties properties, RestClient restClient, Clock clock) {
        return new ApnsClient(
                properties,
                new ApnsProviderTokenProvider(properties, clock),
                restClient
        );
    }

    private ApnsProperties properties() {
        return new ApnsProperties("development", "team", "key", "bundle", privateKey());
    }

    private ApnsMessage message() {
        return new ApnsMessage("token", "{}", Instant.parse("2026-09-08T00:05:00Z"));
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

    private record TestClient(ApnsClient client, MockRestServiceServer server) {
    }

}
