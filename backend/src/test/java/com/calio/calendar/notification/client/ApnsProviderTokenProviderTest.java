package com.calio.calendar.notification.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApnsProviderTokenProviderTest {

    @Test
    @DisplayName("provider JWT는 50분 동안 재사용하고 이후 새로 발급한다")
    void givenCachedProviderToken_whenReuseDurationExpires_thenIssuesNewToken() {
        // given
        MutableClock clock = new MutableClock(Instant.parse("2026-09-09T00:00:00Z"));
        ApnsProviderTokenProvider provider = new ApnsProviderTokenProvider(properties(), clock);
        String firstToken = provider.getProviderToken();

        // when
        clock.advance(Duration.ofMinutes(49));
        String reusedToken = provider.getProviderToken();
        clock.advance(Duration.ofMinutes(1));
        String renewedToken = provider.getProviderToken();

        // then
        assertThat(reusedToken).isEqualTo(firstToken);
        assertThat(renewedToken).isNotEqualTo(firstToken);
    }

    private ApnsProperties properties() {
        return new ApnsProperties("development", "team", "key", "bundle", privateKey());
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

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
