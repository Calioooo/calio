package com.calio.calendar.holiday.client;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.holiday.client.dto.HolidayApiItem;
import com.calio.calendar.holiday.client.dto.HolidayApiResponse;
import com.calio.calendar.holiday.usecase.HolidayApiClient;
import com.calio.calendar.holiday.usecase.dto.NationalHolidayContent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class PublicDataPortalHolidayApiClient implements HolidayApiClient {

  private static final Logger log = LoggerFactory.getLogger(PublicDataPortalHolidayApiClient.class);
  private static final int MAX_RETRY_COUNT = 1;
  private static final DateTimeFormatter PROVIDER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

  private final HolidayApiProperties holidayApiProperties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public PublicDataPortalHolidayApiClient(
      HolidayApiProperties holidayApiProperties,
      ObjectMapper objectMapper,
      @Qualifier("publicDataPortalHolidayRestClient") RestClient restClient) {
    this.holidayApiProperties = holidayApiProperties;
    this.objectMapper = objectMapper;
    this.restClient = restClient;
  }

  @Override
  public List<NationalHolidayContent> fetchHolidays(int year) {
    if (!holidayApiProperties.hasServiceKey()) {
      throw new CalioException(ErrorCode.HOLIDAY_API_CONFIGURATION_MISSING);
    }

    try {
      HolidayApiResponse response =
          HolidayApiResponse.fromJson(fetchResponseBodyWithRetry(year), objectMapper);
      if (!response.isSuccess()) {
        log.warn(
            "Holiday API returned a failed result. year={} resultCode={}",
            year,
            response.resultCode());
        throw new CalioException(ErrorCode.EXTERNAL_API_UNAVAILABLE);
      }
      return response.items().stream()
          .filter(item -> "Y".equals(item.isHoliday()))
          .map(this::toNationalHolidayContent)
          .toList();
    } catch (JacksonException | RestClientException exception) {
      throw new CalioException(ErrorCode.EXTERNAL_API_UNAVAILABLE, exception);
    }
  }

  private NationalHolidayContent toNationalHolidayContent(HolidayApiItem item) {
    return new NationalHolidayContent(
        LocalDate.parse(item.localDate(), PROVIDER_DATE_FORMAT), item.dateName());
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
            exception.getMessage());
      }
    }
  }

  private String fetchResponseBody(int year) {
    return restClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .queryParam("solYear", year)
                    .queryParam("ServiceKey", "{serviceKey}")
                    .queryParam("_type", "json")
                    .queryParam("numOfRows", 365)
                    .build(holidayApiProperties.getServiceKey()))
        .retrieve()
        .body(String.class);
  }
}
