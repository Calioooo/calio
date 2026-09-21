package com.calio.calendar.holiday.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class PublicDataPortalHolidayApiClientTest {

  @Test
  @DisplayName("공휴일 API service key가 없으면 명시적인 설정 오류를 반환한다")
  void givenMissingServiceKey_whenFetchHolidays_thenThrowsConfigurationError() {
    // given
    HolidayApiProperties properties = new HolidayApiProperties();
    PublicDataPortalHolidayApiClient client =
        new PublicDataPortalHolidayApiClient(
            properties, new ObjectMapper(), RestClient.builder().build());

    // when, then
    assertThatThrownBy(() -> client.fetchHolidays(2026))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.HOLIDAY_API_CONFIGURATION_MISSING));
  }

  @Test
  @DisplayName("공휴일 API service key는 null, 빈 값, 공백만 있는 값을 설정되지 않은 것으로 판단한다")
  void givenBlankServiceKeys_whenCheckConfiguration_thenReportsMissingConfiguration() {
    // given
    HolidayApiProperties properties = new HolidayApiProperties();

    // when, then
    assertThat(properties.hasServiceKey()).isFalse();

    properties.setServiceKey("");
    assertThat(properties.hasServiceKey()).isFalse();

    properties.setServiceKey("   ");
    assertThat(properties.hasServiceKey()).isFalse();

    properties.setServiceKey("test-service-key");
    assertThat(properties.hasServiceKey()).isTrue();
  }

  @Test
  @DisplayName("공휴일 API가 실패 resultCode를 반환하면 외부 API 오류를 반환한다")
  void givenFailedProviderResult_whenFetchHolidays_thenThrowsExternalApiUnavailable() {
    HolidayApiProperties properties = new HolidayApiProperties();
    properties.setBaseUrl("https://holiday.example.test");
    properties.setServiceKey("test-service-key");
    RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(properties.getBaseUrl());
    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    PublicDataPortalHolidayApiClient client =
        new PublicDataPortalHolidayApiClient(
            properties, new ObjectMapper(), restClientBuilder.build());
    server
        .expect(request -> {})
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "header": {"resultCode": "99"},
                    "body": {"items": {}}
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.fetchHolidays(2026))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_UNAVAILABLE));
    server.verify();
  }
}
