package com.calio.calendar.holiday.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HolidayApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("배열 형태의 items.item을 HolidayApiItem 목록으로 반환한다")
    void givenArrayItem_whenParseHolidayApiResponse_thenReadsItems() {
        // given
        String json = """
                {
                  "response": {
                    "header": {
                      "resultCode": "00"
                    },
                    "body": {
                      "items": {
                        "item": [
                          {
                            "locdate": 20260101,
                            "dateName": "신정",
                            "isHoliday": "Y",
                            "dateKind": "01",
                            "seq": 1
                          },
                          {
                            "locdate": "20260214",
                            "dateName": "임시 기념일",
                            "isHoliday": "N"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        // when
        HolidayApiResponse response = HolidayApiResponse.fromJson(json, objectMapper);

        // then
        assertThat(response.resultCode()).isEqualTo("00");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().localDate()).isEqualTo("20260101");
        assertThat(response.items().getFirst().dateName()).isEqualTo("신정");
        assertThat(response.items().getFirst().isHoliday()).isEqualTo("Y");
    }

    @Test
    @DisplayName("공휴일 API 응답은 단일 객체 형태의 items.item도 provider item 목록으로 정규화한다")
    void givenSingleObjectItem_whenParseHolidayApiResponse_thenNormalizesItemList() {
        // given
        String json = """
                {
                  "response": {
                    "header": {
                      "resultCode": "00"
                    },
                    "body": {
                      "items": {
                        "item": {
                          "locdate": "20261003",
                          "dateName": "개천절",
                          "isHoliday": "Y"
                        }
                      }
                    }
                  }
                }
                """;

        // when
        HolidayApiResponse response = HolidayApiResponse.fromJson(json, objectMapper);

        // then
        assertThat(response.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.localDate()).isEqualTo("20261003");
                    assertThat(item.dateName()).isEqualTo("개천절");
                    assertThat(item.isHoliday()).isEqualTo("Y");
                });
    }
}
