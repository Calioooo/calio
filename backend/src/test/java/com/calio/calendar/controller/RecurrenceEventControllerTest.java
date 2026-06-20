package com.calio.calendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
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
    @DisplayName("사용자는 반복 일정 전체를 수정하면 rule과 occurrence 일정 집합을 함께 재구성한다")
    void givenExistingRecurrenceEvent_whenUpdateWholeRecurrenceEvent_thenUpdatesRuleAndRebuildsOccurrences()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Original daily",
                "2026-11-01",
                "2026-11-03",
                "DAILY"
        );
        String requestBody = recurrenceUpdateRequest(
                "Updated weekly",
                "Updated series",
                "2026-11-01T09:00:00Z",
                "2026-11-15T10:00:00Z",
                "WEEKLY"
        );

        // when
        mockMvc.perform(put("/api/recurrence-events/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$.recurrenceTitle").value("Updated weekly"))
                .andExpect(jsonPath("$.recurrenceDescription").value("Updated series"))
                .andExpect(jsonPath("$.recurrenceStartDate").value("2026-11-01"))
                .andExpect(jsonPath("$.recurrenceEndDate").value("2026-11-15"))
                .andExpect(jsonPath("$.recurrenceStartTime").value("09:00:00"))
                .andExpect(jsonPath("$.recurrenceEndTime").value("10:00:00"))
                .andExpect(jsonPath("$.recurrenceFrequency").value("WEEKLY"));

        var occurrences = eventRepository.findByRecurrenceIdOrderByStartAtAsc(recurrenceId);
        assertThat(occurrences)
                .hasSize(3)
                .allSatisfy(event -> {
                    assertThat(event.getTitle()).isEqualTo("Updated weekly");
                    assertThat(event.getDescription()).isEqualTo("Updated series");
                    assertThat(event.getRecurrenceId()).contains(recurrenceId);
                });
        assertThat(occurrences)
                .extracting(event -> event.getStartAt().toString())
                .containsExactly(
                        "2026-11-01T09:00:00Z",
                        "2026-11-08T09:00:00Z",
                        "2026-11-15T09:00:00Z"
                );
    }

    @Test
    @DisplayName("반복 일정 전체 수정에서 omitted title은 유지하고 explicit null description은 null로 갱신한다")
    void givenOmittedTitleAndNullDescription_whenUpdateWholeRecurrenceEvent_thenRetainsTitleAndClearsDescription()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEventWithDescription(
                "Nullable recurrence",
                "Original description",
                "2026-11-21",
                "2026-11-22",
                "DAILY"
        );
        String requestBody = """
                {
                  "description": null,
                  "startAt": "2026-11-21T09:00:00Z",
                  "endAt": "2026-11-22T10:00:00Z",
                  "recurrenceFrequency": "DAILY"
                }
                """;

        // when
        MvcResult result = mockMvc.perform(put("/api/recurrence-events/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceTitle").value("Nullable recurrence"))
                .andReturn();

        assertThat(readResponse(result).get("recurrenceDescription").isNull()).isTrue();
        assertThat(eventRepository.findByRecurrenceIdOrderByStartAtAsc(recurrenceId))
                .allSatisfy(event -> {
                    assertThat(event.getTitle()).isEqualTo("Nullable recurrence");
                    assertThat(event.getDescription()).isNull();
                });
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청 body가 object가 아니면 RECURRENCE_UPDATE_BODY_NOT_OBJECT를 받는다")
    void givenNonObjectBody_whenUpdateWholeRecurrenceEvent_thenReturnsBodyNotObject() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Body shape",
                "2026-11-25",
                "2026-11-26",
                "DAILY"
        );

        // when, then
        assertUpdateError(recurrenceId, "[]", "RECURRENCE_UPDATE_BODY_NOT_OBJECT");
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청 body의 updateScope는 RECURRENCE_UPDATE_SCOPE_UNSUPPORTED를 받는다")
    void givenUpdateScopeBodyField_whenUpdateWholeRecurrenceEvent_thenReturnsScopeUnsupported() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Body scope",
                "2026-11-27",
                "2026-11-28",
                "DAILY"
        );
        String requestBody = """
                {
                  "title": "Scope",
                  "startAt": "2026-11-27T09:00:00Z",
                  "endAt": "2026-11-28T10:00:00Z",
                  "recurrenceFrequency": "DAILY",
                  "updateScope": "ALL"
                }
                """;

        // when, then
        assertUpdateError(recurrenceId, requestBody, "RECURRENCE_UPDATE_SCOPE_UNSUPPORTED");
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청 query의 updateScope는 RECURRENCE_UPDATE_SCOPE_UNSUPPORTED를 받는다")
    void givenUpdateScopeQueryParameter_whenUpdateWholeRecurrenceEvent_thenReturnsScopeUnsupported()
            throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Query scope",
                "2026-11-29",
                "2026-11-30",
                "DAILY"
        );

        // when
        mockMvc.perform(put("/api/recurrence-events/{recurrenceId}", recurrenceId)
                        .param("updateScope", "ALL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRecurrenceUpdateRequest()))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RECURRENCE_UPDATE_SCOPE_UNSUPPORTED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청에 지원하지 않는 field가 있으면 RECURRENCE_UPDATE_UNSUPPORTED_FIELD를 받는다")
    void givenUnsupportedBodyField_whenUpdateWholeRecurrenceEvent_thenReturnsUnsupportedField() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Unsupported field",
                "2026-12-01",
                "2026-12-02",
                "DAILY"
        );
        String requestBody = """
                {
                  "title": "Unsupported",
                  "startAt": "2026-12-01T09:00:00Z",
                  "endAt": "2026-12-02T10:00:00Z",
                  "recurrenceFrequency": "DAILY",
                  "unknown": true
                }
                """;

        // when, then
        assertUpdateError(recurrenceId, requestBody, "RECURRENCE_UPDATE_UNSUPPORTED_FIELD");
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청의 title이 공백이면 RECURRENCE_UPDATE_TITLE_BLANK를 받는다")
    void givenBlankTitle_whenUpdateWholeRecurrenceEvent_thenReturnsTitleBlank() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Blank title",
                "2026-12-03",
                "2026-12-04",
                "DAILY"
        );
        String requestBody = recurrenceUpdateRequest(
                " ",
                null,
                "2026-12-03T09:00:00Z",
                "2026-12-04T10:00:00Z",
                "DAILY"
        );

        // when, then
        assertUpdateError(recurrenceId, requestBody, "RECURRENCE_UPDATE_TITLE_BLANK");
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청의 startAt이 없으면 RECURRENCE_UPDATE_START_AT_REQUIRED를 받는다")
    void givenMissingStartAt_whenUpdateWholeRecurrenceEvent_thenReturnsStartAtRequired() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Missing start",
                "2026-12-05",
                "2026-12-06",
                "DAILY"
        );
        String requestBody = """
                {
                  "title": "Missing start",
                  "endAt": "2026-12-06T10:00:00Z",
                  "recurrenceFrequency": "DAILY"
                }
                """;

        // when, then
        assertUpdateError(recurrenceId, requestBody, "RECURRENCE_UPDATE_START_AT_REQUIRED");
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청의 startAt 형식이 잘못되면 RECURRENCE_UPDATE_START_AT_INVALID를 받는다")
    void givenInvalidStartAt_whenUpdateWholeRecurrenceEvent_thenReturnsStartAtInvalid() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Invalid start",
                "2026-12-07",
                "2026-12-08",
                "DAILY"
        );
        String requestBody = recurrenceUpdateRequest(
                "Invalid start",
                null,
                "not-an-instant",
                "2026-12-08T10:00:00Z",
                "DAILY"
        );

        // when, then
        assertUpdateError(recurrenceId, requestBody, "RECURRENCE_UPDATE_START_AT_INVALID");
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청의 endAt이 없으면 RECURRENCE_UPDATE_END_AT_REQUIRED를 받는다")
    void givenMissingEndAt_whenUpdateWholeRecurrenceEvent_thenReturnsEndAtRequired() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Missing end",
                "2026-12-09",
                "2026-12-10",
                "DAILY"
        );
        String requestBody = """
                {
                  "title": "Missing end",
                  "startAt": "2026-12-09T09:00:00Z",
                  "recurrenceFrequency": "DAILY"
                }
                """;

        // when, then
        assertUpdateError(recurrenceId, requestBody, "RECURRENCE_UPDATE_END_AT_REQUIRED");
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청의 endAt 형식이 잘못되면 RECURRENCE_UPDATE_END_AT_INVALID를 받는다")
    void givenInvalidEndAt_whenUpdateWholeRecurrenceEvent_thenReturnsEndAtInvalid() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Invalid end",
                "2026-12-11",
                "2026-12-12",
                "DAILY"
        );
        String requestBody = recurrenceUpdateRequest(
                "Invalid end",
                null,
                "2026-12-11T09:00:00Z",
                "not-an-instant",
                "DAILY"
        );

        // when, then
        assertUpdateError(recurrenceId, requestBody, "RECURRENCE_UPDATE_END_AT_INVALID");
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청의 recurrenceFrequency가 없으면 RECURRENCE_UPDATE_FREQUENCY_REQUIRED를 받는다")
    void givenMissingFrequency_whenUpdateWholeRecurrenceEvent_thenReturnsFrequencyRequired() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Missing frequency",
                "2026-12-13",
                "2026-12-14",
                "DAILY"
        );
        String requestBody = """
                {
                  "title": "Missing frequency",
                  "startAt": "2026-12-13T09:00:00Z",
                  "endAt": "2026-12-14T10:00:00Z"
                }
                """;

        // when, then
        assertUpdateError(recurrenceId, requestBody, "RECURRENCE_UPDATE_FREQUENCY_REQUIRED");
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청의 recurrenceFrequency 값이 잘못되면 RECURRENCE_UPDATE_FREQUENCY_INVALID를 받는다")
    void givenInvalidFrequency_whenUpdateWholeRecurrenceEvent_thenReturnsFrequencyInvalid() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Invalid frequency",
                "2026-12-15",
                "2026-12-16",
                "DAILY"
        );
        String requestBody = recurrenceUpdateRequest(
                "Invalid frequency",
                null,
                "2026-12-15T09:00:00Z",
                "2026-12-16T10:00:00Z",
                "HOURLY"
        );

        // when, then
        assertUpdateError(recurrenceId, requestBody, "RECURRENCE_UPDATE_FREQUENCY_INVALID");
    }

    @Test
    @DisplayName("반복 일정 전체 수정 요청의 startAt이 endAt보다 빠르지 않으면 RECURRENCE_UPDATE_TIME_RANGE_INVALID를 받는다")
    void givenInvalidTimeRange_whenUpdateWholeRecurrenceEvent_thenReturnsTimeRangeInvalid() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent(
                "Invalid update range",
                "2026-12-17",
                "2026-12-18",
                "DAILY"
        );
        String requestBody = recurrenceUpdateRequest(
                "Invalid update range",
                null,
                "2026-12-17T09:00:00Z",
                "2026-12-17T09:00:00Z",
                "DAILY"
        );

        // when, then
        assertUpdateError(recurrenceId, requestBody, "RECURRENCE_UPDATE_TIME_RANGE_INVALID");
    }

    @Test
    @DisplayName("존재하지 않는 반복 일정 id로 전체 수정하면 RECURRENCE_EVENT_NOT_FOUND를 받는다")
    void givenMissingRecurrenceId_whenUpdateWholeRecurrenceEvent_thenReturnsRecurrenceEventNotFound()
            throws Exception {
        // given
        long missingRecurrenceId = 999999L;

        // when, then
        mockMvc.perform(put("/api/recurrence-events/{recurrenceId}", missingRecurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRecurrenceUpdateRequest()))
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

    private long createRecurrenceEventWithDescription(
            String recurrenceTitle,
            String recurrenceDescription,
            String recurrenceStartDate,
            String recurrenceEndDate,
            String recurrenceFrequency
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurrenceRequest(
                                recurrenceTitle,
                                recurrenceDescription,
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

    private String recurrenceUpdateRequest(
            String title,
            String description,
            String startAt,
            String endAt,
            String recurrenceFrequency
    ) {
        return """
                {
                  "title": %s,
                  "description": %s,
                  "startAt": "%s",
                  "endAt": "%s",
                  "recurrenceFrequency": "%s"
                }
                """.formatted(
                toJsonString(title),
                toJsonString(description),
                startAt,
                endAt,
                recurrenceFrequency
        );
    }

    private String validRecurrenceUpdateRequest() {
        return recurrenceUpdateRequest(
                "Valid update",
                null,
                "2026-12-20T09:00:00Z",
                "2026-12-21T10:00:00Z",
                "DAILY"
        );
    }

    private void assertUpdateError(long recurrenceId, String requestBody, String errorCode) throws Exception {
        mockMvc.perform(put("/api/recurrence-events/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(errorCode))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
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
