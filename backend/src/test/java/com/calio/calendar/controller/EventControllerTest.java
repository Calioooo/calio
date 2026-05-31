package com.calio.calendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("사용자는 단일 시간 일정을 생성하면 서버가 생성한 감사 필드가 포함된 일정을 받는다")
    void givenValidEventRequest_whenCreateEvent_thenReturnsPersistedEventWithServerManagedAuditFields()
            throws Exception {
        // given
        String requestBody = """
                {
                  "title": "Planning",
                  "description": "Weekly planning",
                  "startAt": "2026-06-01T09:00:00+09:00",
                  "endAt": "2026-06-01T10:00:00+09:00",
                  "createdAt": "2000-01-01T00:00:00Z",
                  "updatedAt": "2000-01-01T00:00:00Z"
                }
                """;

        // when
        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Planning"))
                .andExpect(jsonPath("$.description").value("Weekly planning"))
                .andExpect(jsonPath("$.startAt").value("2026-06-01T09:00:00+09:00"))
                .andExpect(jsonPath("$.endAt").value("2026-06-01T10:00:00+09:00"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andReturn();

        JsonNode response = readResponse(result);
        assertThat(response.get("createdAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");
        assertThat(response.get("updatedAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("사용자는 공백 제목으로 일정을 생성할 수 없다")
    void givenBlankTitle_whenCreateEvent_thenReturnsValidationFailed() throws Exception {
        // given
        String requestBody = """
                {
                  "title": " ",
                  "startAt": "2026-06-01T09:00:00+09:00",
                  "endAt": "2026-06-01T10:00:00+09:00"
                }
                """;

        // when
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    @DisplayName("사용자는 시작 시각이 종료 시각보다 빠르지 않은 일정을 생성할 수 없다")
    void givenStartAtIsNotEarlierThanEndAt_whenCreateEvent_thenReturnsInvalidTimeRange() throws Exception {
        // given
        String requestBody = """
                {
                  "title": "Planning",
                  "startAt": "2026-06-01T10:00:00+09:00",
                  "endAt": "2026-06-01T10:00:00+09:00"
                }
                """;

        // when
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    @DisplayName("사용자는 생성된 일정 id로 단일 일정을 조회할 수 있다")
    void givenExistingEventId_whenGetEvent_thenReturnsEvent() throws Exception {
        // given
        long eventId = createEvent("Review", "2026-06-02T09:00:00+09:00", "2026-06-02T10:00:00+09:00");

        // when
        mockMvc.perform(get("/api/events/{eventId}", eventId))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.title").value("Review"));
    }

    @Test
    @DisplayName("사용자는 존재하지 않는 일정 id를 조회하면 EVENT_NOT_FOUND를 받는다")
    void givenMissingEventId_whenGetEvent_thenReturnsEventNotFound() throws Exception {
        // given
        long missingEventId = 999999L;

        // when
        mockMvc.perform(get("/api/events/{eventId}", missingEventId))
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    @DisplayName("사용자는 시작 시각 범위에 포함되는 일정을 시작 시각 오름차순으로 조회한다")
    void givenEventsAcrossRangeBoundaries_whenListEvents_thenReturnsInclusiveRangeSortedByStartAt()
            throws Exception {
        // given
        createEvent("Before", "2026-06-03T08:59:59+09:00", "2026-06-03T09:30:00+09:00");
        long lowerBoundaryId = createEvent("Lower", "2026-06-03T09:00:00+09:00", "2026-06-03T10:00:00+09:00");
        long middleId = createEvent("Middle", "2026-06-03T10:00:00+09:00", "2026-06-03T11:00:00+09:00");
        long upperBoundaryId = createEvent("Upper", "2026-06-03T11:00:00+09:00", "2026-06-03T12:00:00+09:00");
        createEvent("After", "2026-06-03T11:00:01+09:00", "2026-06-03T12:30:00+09:00");

        // when
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-06-03T09:00:00+09:00")
                        .param("to", "2026-06-03T11:00:00+09:00"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(lowerBoundaryId))
                .andExpect(jsonPath("$[1].id").value(middleId))
                .andExpect(jsonPath("$[2].id").value(upperBoundaryId));
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

    private JsonNode readResponse(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content);
    }
}
