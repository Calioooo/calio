package com.calio.calendar.client;

import com.calio.calendar.client.dto.HolidayApiResponse;
import com.calio.calendar.config.HolidayApiProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class HolidayApiClient {

    private static final String GET_REST_DE_INFO_URL =
            "http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";

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
                .baseUrl(GET_REST_DE_INFO_URL)
                .build();
    }

    public HolidayApiResponse fetchHolidays(int year) throws JacksonException {
        if (!holidayApiProperties.hasServiceKey()) {
            throw new IllegalStateException("Holiday API service key is missing");
        }

        String responseBody = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("solYear", year)
                        .queryParam("ServiceKey", holidayApiProperties.getServiceKey())
                        .queryParam("_type", "json")
                        .queryParam("numOfRows", 365)
                        .build())
                .retrieve()
                .body(String.class);

        return HolidayApiResponse.fromJson(responseBody, objectMapper);
    }
}
