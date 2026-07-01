package com.calio.calendar.client;

import com.calio.calendar.client.dto.HolidayApiResponse;
import com.calio.calendar.config.HolidayApiProperties;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class HolidayApiClient {

    private static final int MAX_RETRY_COUNT = 1;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final HolidayApiProperties holidayApiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public HolidayApiClient(
            HolidayApiProperties holidayApiProperties,
            ObjectMapper objectMapper
    ) {
        this.holidayApiProperties = holidayApiProperties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(holidayApiProperties.getBaseUrl())
                .requestFactory(createRequestFactory())
                .build();
    }

    public HolidayApiResponse fetchHolidays(int year) throws JacksonException {
        if (!holidayApiProperties.hasServiceKey()) {
            throw new IllegalStateException("Holiday API service key is missing");
        }

        String responseBody = fetchResponseBodyWithRetry(year);
        return HolidayApiResponse.fromJson(responseBody, objectMapper);
    }

    private String fetchResponseBodyWithRetry(int year) {
        int attempt = 0;
        while (true) {
            try {
                return fetchResponseBody(year);
            } catch (ResourceAccessException exception) {
                if (attempt >= MAX_RETRY_COUNT) {
                    throw exception;
                }
                attempt++;
            }
        }
    }

    private String fetchResponseBody(int year) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("solYear", year)
                        .queryParam("ServiceKey", holidayApiProperties.getServiceKey())
                        .queryParam("_type", "json")
                        .queryParam("numOfRows", 365)
                        .build())
                .retrieve()
                .body(String.class);
    }

    private SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }
}
