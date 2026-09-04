package com.calio.calendar.external.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class GoogleOAuthPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GoogleOAuthPropertiesConfiguration.class);

    @Test
    @DisplayName("Calendar Events URL은 HTTPS 설정만 허용한다")
    void givenHttpCalendarEventsUrl_whenConfigured_thenRejectsSetting() {
        // given
        GoogleOAuthProperties properties = new GoogleOAuthProperties();

        // when, then
        assertThatThrownBy(() -> properties.setCalendarEventsUrl("http://calendar.example.test/events"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Google Calendar Events URL must use HTTPS");
    }

    @Test
    @DisplayName("HTTP Calendar Events URL은 application binding 단계에서 시작을 막는다")
    void givenHttpCalendarEventsUrl_whenBindingProperties_thenFailsApplicationStartup() {
        contextRunner.withPropertyValues(
                "external.google.oauth.calendar-events-url=http://calendar.example.test/events")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasRootCauseMessage("Google Calendar Events URL must use HTTPS"));
    }

    @Test
    @DisplayName("Calendar Events URL은 HTTPS 설정을 유지한다")
    void givenHttpsCalendarEventsUrl_whenConfigured_thenStoresSetting() {
        // given
        GoogleOAuthProperties properties = new GoogleOAuthProperties();

        // when
        properties.setCalendarEventsUrl("https://calendar.example.test/events");

        // then
        assertThat(properties.getCalendarEventsUrl()).isEqualTo("https://calendar.example.test/events");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GoogleOAuthProperties.class)
    static class GoogleOAuthPropertiesConfiguration {
    }
}
