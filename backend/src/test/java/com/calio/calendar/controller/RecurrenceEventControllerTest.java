package com.calio.calendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.repository.EventRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-recurrence-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class RecurrenceEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Test
    @DisplayName("사용자는 반복 일정을 생성하면 rule과 inclusive 범위의 occurrence 일정을 함께 저장한다")
    void givenDailyRecurrenceRequest_whenCreateRecurrenceEvent_thenStoresRuleAndOccurrences()
            throws Exception {
        // given
        String requestBody = recurrenceRequest(
                "Daily standup",
                "Team sync",
                "2026-08-01",
                "2026-08-03",
                "09:00:00",
                "10:00:00",
                "DAILY"
        );

        // when
        MvcResult createResult = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrenceId").isNumber())
                .andExpect(jsonPath("$.recurrenceTitle").value("Daily standup"))
                .andExpect(jsonPath("$.recurrenceDescription").value("Team sync"))
                .andExpect(jsonPath("$.recurrenceStartDate").value("2026-08-01"))
                .andExpect(jsonPath("$.recurrenceEndDate").value("2026-08-03"))
                .andExpect(jsonPath("$.recurrenceStartTime").value("09:00:00"))
                .andExpect(jsonPath("$.recurrenceEndTime").value("10:00:00"))
                .andExpect(jsonPath("$.recurrenceFrequency").value("DAILY"))
                .andReturn();

        long recurrenceId = readResponse(createResult).get("recurrenceId").asLong();
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-08-04T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].title").value("Daily standup"))
                .andExpect(jsonPath("$[0].description").value("Team sync"))
                .andExpect(jsonPath("$[0].startAt").value("2026-08-01T09:00:00Z"))
                .andExpect(jsonPath("$[0].endAt").value("2026-08-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$[0].isRecurrenceOccurrence").value(true))
                .andExpect(jsonPath("$[1].startAt").value("2026-08-02T09:00:00Z"))
                .andExpect(jsonPath("$[1].recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$[1].isRecurrenceOccurrence").value(true))
                .andExpect(jsonPath("$[2].startAt").value("2026-08-03T09:00:00Z"))
                .andExpect(jsonPath("$[2].recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$[2].isRecurrenceOccurrence").value(true));
    }

    @Test
    @DisplayName("사용자는 생성된 반복 일정 id로 저장된 rule을 조회할 수 있다")
    void givenExistingRecurrenceId_whenGetRecurrenceEvent_thenReturnsStoredRule() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Weekly review",
                "2026-08-11",
                "2026-08-25",
                "WEEKLY"
        );

        // when
        mockMvc.perform(get("/api/recurrence-events/{recurrenceId}", recurrenceId))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$.recurrenceTitle").value("Weekly review"))
                .andExpect(jsonPath("$.recurrenceStartDate").value("2026-08-11"))
                .andExpect(jsonPath("$.recurrenceEndDate").value("2026-08-25"))
                .andExpect(jsonPath("$.recurrenceFrequency").value("WEEKLY"));
    }

    @Test
    @DisplayName("사용자는 반복 일정 전체를 수정하면 rule 응답과 occurrence row set이 함께 갱신된다")
    void givenExistingRecurrenceEvent_whenPatchRecurrenceEvent_thenUpdatesRuleAndRebuildsOccurrences()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Original recurrence",
                "2026-11-01",
                "2026-11-03",
                "DAILY"
        );

        // when
        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated recurrence",
                                  "description": " ",
                                  "startAt": "2026-11-02T11:00:00Z",
                                  "endAt": "2026-11-09T12:00:00Z",
                                  "recurrenceFrequency": "WEEKLY"
                                }
                                """))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$.recurrenceTitle").value("Updated recurrence"))
                .andExpect(jsonPath("$.recurrenceDescription").value(" "))
                .andExpect(jsonPath("$.recurrenceStartDate").value("2026-11-02"))
                .andExpect(jsonPath("$.recurrenceEndDate").value("2026-11-09"))
                .andExpect(jsonPath("$.recurrenceStartTime").value("11:00:00"))
                .andExpect(jsonPath("$.recurrenceEndTime").value("12:00:00"))
                .andExpect(jsonPath("$.recurrenceFrequency").value("WEEKLY"));

        assertStoredOccurrences(
                recurrenceId,
                "2026-11-02T11:00:00Z",
                "2026-11-09T11:00:00Z"
        );
        assertStoredOccurrenceDetails(recurrenceId, "Updated recurrence", " ", "12:00:00Z");
    }

    @Test
    @DisplayName("반복 일정 전체 수정에서 null 필드는 기존 rule 값을 변경하지 않는다")
    void givenNullFields_whenPatchRecurrenceEvent_thenPreservesExistingRuleValues() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Preserved recurrence",
                "2026-11-11",
                "2026-11-12",
                "DAILY"
        );

        // when
        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": null,
                                  "description": null,
                                  "startAt": null,
                                  "endAt": null,
                                  "recurrenceFrequency": null
                                }
                                """))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceTitle").value("Preserved recurrence"))
                .andExpect(jsonPath("$.recurrenceDescription").doesNotExist())
                .andExpect(jsonPath("$.recurrenceStartDate").value("2026-11-11"))
                .andExpect(jsonPath("$.recurrenceEndDate").value("2026-11-12"))
                .andExpect(jsonPath("$.recurrenceStartTime").value("09:00:00"))
                .andExpect(jsonPath("$.recurrenceEndTime").value("10:00:00"))
                .andExpect(jsonPath("$.recurrenceFrequency").value("DAILY"));
    }

    @Test
    @DisplayName("반복 일정 전체 수정에서 effective time range가 유효하지 않으면 RECURRENCE_UPDATE_TIME_RANGE_INVALID를 받는다")
    void givenInvalidTimeRange_whenPatchRecurrenceEvent_thenReturnsRecurrenceUpdateTimeRangeInvalid()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Invalid update target",
                "2026-11-21",
                "2026-11-22",
                "DAILY"
        );

        // when
        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-11-21T10:00:00Z",
                                  "endAt": "2026-11-21T10:00:00Z"
                                }
                                """))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RECURRENCE_UPDATE_TIME_RANGE_INVALID"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청에 지원하지 않는 필드가 있으면 VALIDATION_FAILED를 받는다")
    void givenUnknownField_whenPatchRecurrenceEvent_thenReturnsValidationFailed()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Unknown field target",
                "2026-11-24",
                "2026-11-25",
                "DAILY"
        );

        // when
        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Blocked by unknown field",
                                  "unsupportedField": "fail"
                                }
                                """))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("반복 일정 전체 수정은 effective instant range가 유효하면 종료 시각이 시작 시각보다 이른 값을 허용한다")
    void givenValidEffectiveInstantRange_whenPatchRecurrenceEvent_thenUpdatesRule()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Overnight target",
                "2026-11-28",
                "2026-11-29",
                "DAILY"
        );

        // when
        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-11-28T23:00:00Z",
                                  "endAt": "2026-11-29T01:00:00Z"
                                }
                                """))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$.recurrenceStartDate").value("2026-11-28"))
                .andExpect(jsonPath("$.recurrenceEndDate").value("2026-11-29"))
                .andExpect(jsonPath("$.recurrenceStartTime").value("23:00:00"))
                .andExpect(jsonPath("$.recurrenceEndTime").value("01:00:00"));

        assertStoredOccurrences(recurrenceId, "2026-11-28T23:00:00Z");
        assertStoredOccurrenceDetails(recurrenceId, "Overnight target", null, "01:00:00Z");
    }

    @Test
    @DisplayName("존재하지 않는 반복 일정 전체 수정은 RECURRENCE_EVENT_NOT_FOUND를 받는다")
    void givenMissingRecurrenceId_whenPatchRecurrenceEvent_thenReturnsRecurrenceEventNotFound()
            throws Exception {
        // given
        long missingRecurrenceId = 999999L;

        // when
        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}", missingRecurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "No target"
                                }
                                """))
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECURRENCE_EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 monthly rule을 생성하면 월 단위 occurrence 일정을 저장한다")
    void givenMonthlyRecurrenceRequest_whenCreateRecurrenceEvent_thenStoresMonthlyOccurrences()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Monthly review",
                "2026-01-15",
                "2026-03-15",
                "MONTHLY"
        );

        // when, then
        assertStoredOccurrences(
                recurrenceId,
                "2026-01-15T09:00:00Z",
                "2026-02-15T09:00:00Z",
                "2026-03-15T09:00:00Z"
        );
    }

    @Test
    @DisplayName("사용자는 yearly rule을 생성하면 연 단위 occurrence 일정을 저장한다")
    void givenYearlyRecurrenceRequest_whenCreateRecurrenceEvent_thenStoresYearlyOccurrences()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Yearly review",
                "2026-05-20",
                "2028-05-20",
                "YEARLY"
        );

        // when, then
        assertStoredOccurrences(
                recurrenceId,
                "2026-05-20T09:00:00Z",
                "2027-05-20T09:00:00Z",
                "2028-05-20T09:00:00Z"
        );
    }

    @Test
    @DisplayName("사용자는 존재하지 않는 반복 일정 id를 조회하면 RECURRENCE_EVENT_NOT_FOUND를 받는다")
    void givenMissingRecurrenceId_whenGetRecurrenceEvent_thenReturnsRecurrenceEventNotFound()
            throws Exception {
        // given
        long missingRecurrenceId = 999999L;

        // when
        mockMvc.perform(get("/api/recurrence-events/{recurrenceId}", missingRecurrenceId))
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECURRENCE_EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 반복 시작 날짜가 종료 날짜보다 늦은 rule을 생성할 수 없다")
    void givenInvalidRecurrenceDateRange_whenCreateRecurrenceEvent_thenReturnsInvalidRecurrenceDateRange()
            throws Exception {
        // given
        String requestBody = recurrenceRequest(
                "Invalid date range",
                null,
                "2026-09-03",
                "2026-09-01",
                "09:00:00",
                "10:00:00",
                "DAILY"
        );

        // when
        mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RECURRENCE_DATE_RANGE"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 공백 제목으로 반복 일정을 생성할 수 없다")
    void givenBlankRecurrenceTitle_whenCreateRecurrenceEvent_thenReturnsValidationFailed()
            throws Exception {
        // given
        String requestBody = recurrenceRequest(
                " ",
                null,
                "2026-09-01",
                "2026-09-02",
                "09:00:00",
                "10:00:00",
                "DAILY"
        );

        // when
        mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 반복 시작 시각이 종료 시각보다 빠르지 않은 rule을 생성할 수 없다")
    void givenInvalidRecurrenceTimeRange_whenCreateRecurrenceEvent_thenReturnsInvalidRecurrenceTimeRange()
            throws Exception {
        // given
        String requestBody = recurrenceRequest(
                "Invalid time range",
                null,
                "2026-09-11",
                "2026-09-12",
                "10:00:00",
                "10:00:00",
                "DAILY"
        );

        // when
        mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RECURRENCE_TIME_RANGE"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 지원하지 않는 반복 주기로 rule을 생성할 수 없다")
    void givenUnsupportedRecurrenceFrequency_whenCreateRecurrenceEvent_thenReturnsValidationFailed()
            throws Exception {
        // given
        String requestBody = recurrenceRequest(
                "Unsupported frequency",
                null,
                "2026-09-21",
                "2026-09-22",
                "09:00:00",
                "10:00:00",
                "HOURLY"
        );

        // when
        mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("MONTHLY rule은 시작 날짜를 anchor로 삼아 존재하지 않는 날짜를 월말로 보정한다")
    void givenMonthlyRecurrenceWithInvalidCalendarDate_whenCreateRecurrenceEvent_thenStoresAdjustedOccurrences()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Monthly billing",
                "2026-01-31",
                "2026-03-31",
                "MONTHLY"
        );

        // when, then
        assertStoredOccurrences(
                recurrenceId,
                "2026-01-31T09:00:00Z",
                "2026-02-28T09:00:00Z",
                "2026-03-31T09:00:00Z"
        );
    }

    @Test
    @DisplayName("YEARLY rule은 시작 날짜를 anchor로 삼아 윤년이 아닌 해의 날짜를 보정한다")
    void givenYearlyRecurrenceWithLeapDay_whenCreateRecurrenceEvent_thenStoresAdjustedOccurrences()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Leap day check",
                "2024-02-29",
                "2026-02-28",
                "YEARLY"
        );

        // when, then
        assertStoredOccurrences(
                recurrenceId,
                "2024-02-29T09:00:00Z",
                "2025-02-28T09:00:00Z",
                "2026-02-28T09:00:00Z"
        );
    }

    @Test
    @DisplayName("사용자는 recurrence occurrence event를 기존 단일 일정 조회 API로 조회한다")
    void givenRecurrenceOccurrenceEventId_whenGetEvent_thenReturnsRecurrenceOccurrenceFields()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Occurrence read",
                "2026-10-01",
                "2026-10-02",
                "DAILY"
        );
        JsonNode events = listEvents("2026-10-01T00:00:00Z", "2026-10-03T00:00:00Z");
        long occurrenceEventId = events.get(0).get("id").asLong();

        // when
        mockMvc.perform(get("/api/events/{eventId}", occurrenceEventId))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(occurrenceEventId))
                .andExpect(jsonPath("$.recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$.isRecurrenceOccurrence").value(true));
    }

    @Test
    @DisplayName("사용자는 일정 범위 조회에서 normal event와 recurrence occurrence event를 함께 startAt 순서로 받는다")
    void givenNormalAndRecurrenceEvents_whenListEvents_thenReturnsMergedStoredEventsSortedByStartAt()
            throws Exception {
        // given
        long normalEventId = createEvent("Normal meeting", "2026-10-10T08:00:00Z", "2026-10-10T08:30:00Z");
        long recurrenceId = createRecurrenceEvent(
                "Recurring meeting",
                "2026-10-10",
                "2026-10-10",
                "DAILY"
        );

        // when
        MvcResult result = mockMvc.perform(get("/api/events")
                        .param("from", "2026-10-10T00:00:00Z")
                        .param("to", "2026-10-10T23:59:59Z"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(normalEventId))
                .andExpect(jsonPath("$[0].isRecurrenceOccurrence").value(false))
                .andExpect(jsonPath("$[1].recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$[1].isRecurrenceOccurrence").value(true))
                .andReturn();
        JsonNode events = readResponse(result);
        assertThat(events.get(0).get("recurrenceId").isNull()).isTrue();
    }

    @Test
    @DisplayName("normal event는 backend domain model에서 recurrenceId가 Optional.empty로 해석된다")
    void givenNormalEvent_whenReadDomainModel_thenRecurrenceIdIsOptionalEmpty() throws Exception {
        // given
        long eventId = createEvent("Normal optional", "2026-10-20T08:00:00Z", "2026-10-20T08:30:00Z");

        // when
        JsonNode event = readResponse(mockMvc.perform(get("/api/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andReturn());

        // then
        assertThat(eventRepository.findById(eventId)).hasValueSatisfying(
                storedEvent -> assertThat(storedEvent.getRecurrenceId()).isEmpty()
        );
        assertThat(event.get("recurrenceId").isNull()).isTrue();
        assertThat(event.get("isRecurrenceOccurrence").asBoolean()).isFalse();
    }

    private long createEvent(String title, String startAt, String endAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "startAt": "%s",
                                  "endAt": "%s"
                                }
                                """.formatted(title, startAt, endAt)))
                .andExpect(status().isCreated())
                .andReturn();

        return readResponse(result).get("id").asLong();
    }

    private long createRecurrenceEvent(
            String recurrenceTitle,
            String recurrenceStartDate,
            String recurrenceEndDate,
            String recurrenceFrequency
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurrenceRequest(
                                recurrenceTitle,
                                null,
                                recurrenceStartDate,
                                recurrenceEndDate,
                                "09:00:00",
                                "10:00:00",
                                recurrenceFrequency
                        )))
                .andExpect(status().isCreated())
                .andReturn();

        return readResponse(result).get("recurrenceId").asLong();
    }

    private JsonNode listEvents(String from, String to) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/events")
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andReturn();

        return readResponse(result);
    }

    private void assertStoredOccurrences(long recurrenceId, String... expectedStartAtValues) {
        var occurrences = eventRepository.findByRecurrenceIdOrderByStartAtAsc(recurrenceId);

        assertThat(occurrences)
                .hasSize(expectedStartAtValues.length)
                .allSatisfy(event -> {
                    assertThat(event.getRecurrenceId()).contains(recurrenceId);
                    assertThat(event.isRecurrenceOccurrence()).isTrue();
                });
        assertThat(occurrences)
                .extracting(event -> event.getStartAt().toString())
                .containsExactly(expectedStartAtValues);
    }

    private void assertStoredOccurrenceDetails(
            long recurrenceId,
            String expectedTitle,
            String expectedDescription,
            String expectedEndAtSuffix
    ) {
        var occurrences = eventRepository.findByRecurrenceIdOrderByStartAtAsc(recurrenceId);

        assertThat(occurrences)
                .allSatisfy(event -> {
                    assertThat(event.getTitle()).isEqualTo(expectedTitle);
                    assertThat(event.getDescription()).isEqualTo(expectedDescription);
                    assertThat(event.getEndAt().toString()).endsWith(expectedEndAtSuffix);
                });
    }

    private String recurrenceRequest(
            String recurrenceTitle,
            String recurrenceDescription,
            String recurrenceStartDate,
            String recurrenceEndDate,
            String recurrenceStartTime,
            String recurrenceEndTime,
            String recurrenceFrequency
    ) {
        return """
                {
                  "recurrenceTitle": "%s",
                  "recurrenceDescription": %s,
                  "recurrenceStartDate": "%s",
                  "recurrenceEndDate": "%s",
                  "recurrenceStartTime": "%s",
                  "recurrenceEndTime": "%s",
                  "recurrenceFrequency": "%s"
                }
                """.formatted(
                recurrenceTitle,
                toJsonString(recurrenceDescription),
                recurrenceStartDate,
                recurrenceEndDate,
                recurrenceStartTime,
                recurrenceEndTime,
                recurrenceFrequency
        );
    }

    private String toJsonString(String value) {
        if (value == null) {
            return "null";
        }

        return "\"%s\"".formatted(value);
    }

    private JsonNode readResponse(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content);
    }
}
