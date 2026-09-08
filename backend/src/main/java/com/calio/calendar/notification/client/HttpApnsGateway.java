package com.calio.calendar.notification.client;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HttpApnsGateway implements ApnsGateway {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final ApnsProperties properties;
    private final HttpClient client;
    @Autowired
    public HttpApnsGateway(ApnsProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .version(HttpClient.Version.HTTP_2)
                .build());
    }

    HttpApnsGateway(ApnsProperties properties, HttpClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public ApnsSendResult send(ApnsMessage message) {
        if (!properties.configured()) {
            return ApnsSendResult.configurationFailure("APNs credentials are not configured");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.host() + "/3/device/" + message.token()))
                    .timeout(REQUEST_TIMEOUT)
                    .header("authorization", "bearer " + providerToken())
                    .header("apns-topic", properties.bundleId())
                    .header("apns-push-type", "alert")
                    .header("apns-expiration", Long.toString(message.expiration().getEpochSecond()))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(message.payload()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String requestId = response.headers().firstValue("apns-id").orElse(null);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new ApnsSendResult(ApnsSendResultType.ACCEPTED, requestId, null);
            }
            String reason = response.body();
            if (response.statusCode() == 410 || reason.contains("BadDeviceToken") || reason.contains("Unregistered")) {
                return new ApnsSendResult(ApnsSendResultType.INVALID_ENDPOINT, requestId, reason);
            }
            if (response.statusCode() >= 500 || response.statusCode() == 429) {
                return new ApnsSendResult(ApnsSendResultType.TRANSIENT_FAILURE, requestId, reason);
            }
            return new ApnsSendResult(ApnsSendResultType.REJECTED, requestId, reason);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ApnsSendResult(ApnsSendResultType.TRANSIENT_FAILURE, null, "interrupted");
        } catch (Exception exception) {
            return new ApnsSendResult(
                    ApnsSendResultType.TRANSIENT_FAILURE,
                    null,
                    exception.getClass().getSimpleName()
            );
        }
    }
    private String providerToken() throws Exception {
        String pem = properties.privateKey()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        ECPrivateKey key = (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(properties.keyId()).build(),
                new JWTClaimsSet.Builder().issuer(properties.teamId()).issueTime(Date.from(Instant.now())).build()
        );
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }
}
