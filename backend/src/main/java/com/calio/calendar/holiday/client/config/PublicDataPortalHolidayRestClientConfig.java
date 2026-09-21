package com.calio.calendar.holiday.client.config;

import com.calio.calendar.holiday.client.HolidayApiProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PublicDataPortalHolidayRestClientConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

  @Bean
  public RestClient publicDataPortalHolidayRestClient(
      RestClient.Builder builder, HolidayApiProperties holidayApiProperties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    return builder
        .baseUrl(holidayApiProperties.getBaseUrl())
        .requestFactory(requestFactory)
        .build();
  }
}
