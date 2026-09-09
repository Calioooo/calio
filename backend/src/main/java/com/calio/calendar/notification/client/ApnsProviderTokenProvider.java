package com.calio.calendar.notification.client;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.stereotype.Component;

@Component
public class ApnsProviderTokenProvider {

    private static final Duration PROVIDER_TOKEN_REUSE_DURATION = Duration.ofMinutes(50);

    private final ApnsProperties properties;
    private final Clock clock;
    private String cachedProviderToken;
    private Instant cachedProviderTokenIssuedAt;

    public ApnsProviderTokenProvider(ApnsProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized String getProviderToken() {
        if (!properties.configured()) {
            throw new ApnsProviderTokenException("APNs credentials are not configured");
        }

        Instant now = clock.instant();
        if (canReuseProviderToken(now)) {
            return cachedProviderToken;
        }

        return createAndCacheProviderToken(now);
    }

    private boolean canReuseProviderToken(Instant now) {
        return cachedProviderToken != null
                && cachedProviderTokenIssuedAt.plus(PROVIDER_TOKEN_REUSE_DURATION).isAfter(now);
    }

    private String createAndCacheProviderToken(Instant now) {
        try {
            String providerToken = createProviderToken(now);
            cachedProviderToken = providerToken;
            cachedProviderTokenIssuedAt = now;
            return providerToken;
        } catch (Exception exception) {
            throw new ApnsProviderTokenException("APNs provider token could not be created", exception);
        }
    }

    private String createProviderToken(Instant now) throws Exception {
        ECPrivateKey key = privateKey();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(properties.keyId()).build(),
                new JWTClaimsSet.Builder()
                        .issuer(properties.teamId())
                        .issueTime(Date.from(now))
                        .build()
        );
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }

    private ECPrivateKey privateKey() throws Exception {
        String pem = properties.privateKey()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }
}
