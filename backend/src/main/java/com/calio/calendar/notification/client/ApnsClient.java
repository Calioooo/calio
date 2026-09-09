package com.calio.calendar.notification.client;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
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

    private final ApnsProperties properties;
    private final RestClient restClient;

    public ApnsClient(
            ApnsProperties properties,
            @Qualifier("apnsRestClient") RestClient restClient
    ) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public ApnsSendResult send(ApnsMessage message) {
        if (!properties.configured()) {
            return ApnsSendResult.configurationFailure("APNs credentials are not configured");
        }

        try {
            String providerToken = providerToken();
            ResponseEntity<String> response = restClient.post()
                    .uri("/3/device/{token}", message.token())
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

    private String providerToken() throws Exception {
        String pem = properties.privateKey()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        ECPrivateKey key = (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))
                );
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(properties.keyId()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(properties.teamId())
                        .issueTime(Date.from(Instant.now()))
                        .build()
        );
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }
}
