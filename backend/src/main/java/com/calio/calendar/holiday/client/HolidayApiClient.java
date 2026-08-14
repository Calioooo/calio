package com.calio.calendar.holiday.client;

import com.calio.calendar.holiday.client.dto.HolidayApiResponse;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class HolidayApiClient {

    private static final Logger log = LoggerFactory.getLogger(HolidayApiClient.class);
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

    public HolidayApiResponse fetchHolidays(int year) {
        if (!holidayApiProperties.hasServiceKey()) {
            throw new CalioException(ErrorCode.HOLIDAY_API_CONFIGURATION_MISSING);
        }

        try {
            String responseBody = fetchResponseBodyWithRetry(year);
            return HolidayApiResponse.fromJson(responseBody, objectMapper);
        } catch (JacksonException | RestClientException exception) {
            throw new CalioException(ErrorCode.EXTERNAL_API_UNAVAILABLE, exception);
        }
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
                log.debug(
                        "Holiday API request failed. year={} attempt={} message={}",
                        year,
                        attempt,
                        exception.getMessage()
                );
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
