package com.calio.calendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.NationalHolidayRepository;
import com.calio.calendar.repository.entity.Event;
import com.calio.calendar.repository.entity.NationalHoliday;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-item-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class CalendarItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private NationalHolidayRepository nationalHolidayRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        nationalHolidayRepository.deleteAll();
    }

    @Test
    @DisplayName("사용자는 일정과 공휴일을 하나의 calendar item 목록에서 결정적 순서로 조회한다")
    void givenEventsAndNationalHolidays_whenListCalendarItems_thenReturnsUnifiedItemsInDeterministicOrder()
            throws Exception {
        // given
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.parse("2026-01-01"), "Beta"));
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.parse("2026-01-01"), "Alpha"));
        Event firstEvent = saveEvent("First", "2025-12-31T15:00:00Z", "2025-12-31T16:00:00Z");
        Event secondEvent = saveEvent("Second", "2025-12-31T15:00:00Z", "2025-12-31T17:00:00Z");
        Event deletedEvent = saveEvent("Deleted", "2025-12-31T15:00:00Z", "2025-12-31T18:00:00Z");
        deletedEvent.softDelete(Instant.parse("2025-12-31T14:00:00Z"));
        eventRepository.saveAndFlush(deletedEvent);

        // when
        MvcResult result = mockMvc.perform(get("/api/calendar-items")
                        .param("from", "2025-12-31T14:59:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].itemType").value("NATIONAL_HOLIDAY"))
                .andExpect(jsonPath("$[0].nationalHoliday.holidayDate").value("2026-01-01"))
                .andExpect(jsonPath("$[0].nationalHoliday.holidayTitle").value("Alpha"))
                .andExpect(jsonPath("$[1].itemType").value("NATIONAL_HOLIDAY"))
                .andExpect(jsonPath("$[1].nationalHoliday.holidayTitle").value("Beta"))
                .andExpect(jsonPath("$[2].itemType").value("EVENT"))
                .andExpect(jsonPath("$[2].event.id").value(firstEvent.getId()))
                .andExpect(jsonPath("$[3].itemType").value("EVENT"))
                .andExpect(jsonPath("$[3].event.id").value(secondEvent.getId()))
                .andReturn();

        JsonNode items = readResponse(result);
        assertThat(items.get(0).get("event").isNull()).isTrue();
        assertThat(items.get(0).get("nationalHoliday").isNull()).isFalse();
        assertThat(items.get(2).get("event").isNull()).isFalse();
        assertThat(items.get(2).get("nationalHoliday").isNull()).isTrue();
        assertThat(containsEventId(items, deletedEvent.getId())).isFalse();
        assertThat(items.get(0).has("sortDateTime")).isFalse();
    }

    @Test
    @DisplayName("calendar item 공휴일 조회는 Instant 범위를 Asia/Seoul LocalDate 범위로 변환한다")
    void givenInstantRangeInsideSeoulDate_whenListCalendarItems_thenIncludesHolidayForThatSeoulDate()
            throws Exception {
        // given
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.parse("2026-01-01"), "Seoul Day"));
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.parse("2026-01-02"), "Outside"));

        // when
        mockMvc.perform(get("/api/calendar-items")
                        .param("from", "2025-12-31T15:30:00Z")
                        .param("to", "2026-01-01T14:30:00Z"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].itemType").value("NATIONAL_HOLIDAY"))
                .andExpect(jsonPath("$[0].nationalHoliday.holidayDate").value("2026-01-01"))
                .andExpect(jsonPath("$[0].nationalHoliday.holidayTitle").value("Seoul Day"));
    }

    @Test
    @DisplayName("calendar item 추가 후에도 기존 일정 목록 API 응답은 EventResponse 배열로 유지된다")
    void givenCalendarItemApiExists_whenListEvents_thenReturnsExistingEventResponseContract()
            throws Exception {
        // given
        Event event = saveEvent("Existing event", "2026-01-03T00:00:00Z", "2026-01-03T01:00:00Z");
        nationalHolidayRepository.save(new NationalHoliday(LocalDate.parse("2026-01-03"), "Holiday"));

        // when
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-01-03T00:00:00Z")
                        .param("to", "2026-01-03T00:00:00Z"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(event.getId()))
                .andExpect(jsonPath("$[0].title").value("Existing event"))
                .andExpect(jsonPath("$[0].startAt").value("2026-01-03T00:00:00Z"))
                .andExpect(jsonPath("$[0].endAt").value("2026-01-03T01:00:00Z"))
                .andExpect(jsonPath("$[0].importantEvent").value(false))
                .andExpect(jsonPath("$[0].createdAt").isString())
                .andExpect(jsonPath("$[0].updatedAt").isString())
                .andExpect(jsonPath("$[0].itemType").doesNotExist())
                .andExpect(jsonPath("$[0].nationalHoliday").doesNotExist());
    }

    @Test
    @DisplayName("사용자는 from이 to보다 늦은 calendar item 범위를 조회할 수 없다")
    void givenFromAfterTo_whenListCalendarItems_thenReturnsInvalidTimeRange() throws Exception {
        // when
        mockMvc.perform(get("/api/calendar-items")
                        .param("from", "2026-01-02T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.message").value("Invalid time range."))
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 필수 calendar item 조회 파라미터 없이 조회할 수 없다")
    void givenMissingRequiredParameter_whenListCalendarItems_thenReturnsValidationFailed()
            throws Exception {
        // when, then
        mockMvc.perform(get("/api/calendar-items")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.*", hasSize(2)));

        mockMvc.perform(get("/api/calendar-items")
                        .param("from", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 ISO date-time 형식이 아닌 시각으로 calendar item을 조회할 수 없다")
    void givenMalformedDateTimeParameter_whenListCalendarItems_thenReturnsValidationFailed()
            throws Exception {
        // when
        mockMvc.perform(get("/api/calendar-items")
                        .param("from", "not-a-date-time")
                        .param("to", "2026-01-01T00:00:00Z"))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    private Event saveEvent(String title, String startAt, String endAt) {
        return eventRepository.saveAndFlush(new Event(
                title,
                null,
                Instant.parse(startAt),
                Instant.parse(endAt)
        ));
    }

    private JsonNode readResponse(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content);
    }

    private boolean containsEventId(JsonNode items, Long eventId) {
        for (JsonNode item : items) {
            JsonNode event = item.get("event");
            if (event != null && !event.isNull() && event.get("id").asLong() == eventId) {
                return true;
            }
        }

        return false;
    }
}
