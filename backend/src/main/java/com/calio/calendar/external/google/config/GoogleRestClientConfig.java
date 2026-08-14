package com.calio.calendar.external.google.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GoogleRestClientConfig {

    @Bean
    public RestClient googleOAuthRestClient(RestClient.Builder builder) {
        return buildRestClient(builder, Duration.ofSeconds(15));
    }

    @Bean
    public RestClient googleCalendarEventsRestClient(RestClient.Builder builder) {
        return buildRestClient(builder, Duration.ofSeconds(30));
    }

    private RestClient buildRestClient(
            RestClient.Builder builder,
            Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(readTimeout);
        return builder.requestFactory(requestFactory).build();
    }
}
