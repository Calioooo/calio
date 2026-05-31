package com.calio.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
class CalioApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void createEventReturnsPersistedEventWithServerManagedAuditFields() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Planning",
                                  "description": "Weekly planning",
                                  "startAt": "2026-06-01T09:00:00+09:00",
                                  "endAt": "2026-06-01T10:00:00+09:00",
                                  "createdAt": "2000-01-01T00:00:00Z",
                                  "updatedAt": "2000-01-01T00:00:00Z"
                                }
                                """))
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
    void createEventRejectsBlankTitleWithValidationFailed() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "startAt": "2026-06-01T09:00:00+09:00",
                                  "endAt": "2026-06-01T10:00:00+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void createEventRejectsStartAtThatIsNotEarlierThanEndAt() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Planning",
                                  "startAt": "2026-06-01T10:00:00+09:00",
                                  "endAt": "2026-06-01T10:00:00+09:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void getEventReturnsExistingEvent() throws Exception {
        long eventId = createEvent("Review", "2026-06-02T09:00:00+09:00", "2026-06-02T10:00:00+09:00");

        mockMvc.perform(get("/api/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.title").value("Review"));
    }

    @Test
    void getEventReturnsEventNotFoundForMissingId() throws Exception {
        mockMvc.perform(get("/api/events/{eventId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void listEventsFiltersByInclusiveStartAtRangeAndSortsAscending() throws Exception {
        createEvent("Before", "2026-06-03T08:59:59+09:00", "2026-06-03T09:30:00+09:00");
        long lowerBoundaryId = createEvent("Lower", "2026-06-03T09:00:00+09:00", "2026-06-03T10:00:00+09:00");
        long middleId = createEvent("Middle", "2026-06-03T10:00:00+09:00", "2026-06-03T11:00:00+09:00");
        long upperBoundaryId = createEvent("Upper", "2026-06-03T11:00:00+09:00", "2026-06-03T12:00:00+09:00");
        createEvent("After", "2026-06-03T11:00:01+09:00", "2026-06-03T12:30:00+09:00");

        mockMvc.perform(get("/api/events")
                        .param("from", "2026-06-03T09:00:00+09:00")
                        .param("to", "2026-06-03T11:00:00+09:00"))
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
