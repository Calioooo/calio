package com.calio.calendar.controller;

import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.entity.Event;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    @Test
    void createEventReturnsStoredEventWithAuditingFields() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Design review",
                                  "description": "Weekly sync",
                                  "startAt": "2026-05-26T01:00:00Z",
                                  "endAt": "2026-05-26T02:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Design review"))
                .andExpect(jsonPath("$.description").value("Weekly sync"))
                .andExpect(jsonPath("$.startAt").value("2026-05-26T01:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2026-05-26T02:00:00Z"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createEventReturnsValidationFailedWhenTitleIsBlank() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   ",
                                  "description": "Weekly sync",
                                  "startAt": "2026-05-26T01:00:00Z",
                                  "endAt": "2026-05-26T02:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("title must not be blank"));
    }

    @Test
    void createEventReturnsValidationFailedWhenStartAtIsMissing() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Design review",
                                  "description": "Weekly sync",
                                  "endAt": "2026-05-26T02:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("startAt is required"));
    }

    @Test
    void createEventReturnsInvalidTimeRangeWhenStartAtIsNotEarlierThanEndAt() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Design review",
                                  "description": "Weekly sync",
                                  "startAt": "2026-05-26T02:00:00Z",
                                  "endAt": "2026-05-26T02:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.message").value("startAt must be earlier than endAt"));
    }

    @Test
    void getEventReturnsStoredEvent() throws Exception {
        Event event = eventRepository.save(new Event(
                "Planning",
                "Q2 roadmap",
                Instant.parse("2026-05-26T03:00:00Z"),
                Instant.parse("2026-05-26T04:00:00Z")
        ));

        mockMvc.perform(get("/api/events/{eventId}", event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId()))
                .andExpect(jsonPath("$.title").value("Planning"))
                .andExpect(jsonPath("$.description").value("Q2 roadmap"))
                .andExpect(jsonPath("$.startAt").value("2026-05-26T03:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2026-05-26T04:00:00Z"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void getEventReturnsEventNotFoundWhenMissing() throws Exception {
        mockMvc.perform(get("/api/events/{eventId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Event not found: 999"));
    }

    @Test
    void listEventsFiltersInclusiveRangeAndSortsByStartAtAscending() throws Exception {
        eventRepository.save(new Event(
                "Outside",
                null,
                Instant.parse("2026-05-26T00:30:00Z"),
                Instant.parse("2026-05-26T00:45:00Z")
        ));
        eventRepository.save(new Event(
                "Boundary start",
                null,
                Instant.parse("2026-05-26T01:00:00Z"),
                Instant.parse("2026-05-26T01:30:00Z")
        ));
        eventRepository.save(new Event(
                "Middle",
                null,
                Instant.parse("2026-05-26T02:00:00Z"),
                Instant.parse("2026-05-26T02:30:00Z")
        ));
        eventRepository.save(new Event(
                "Boundary end",
                null,
                Instant.parse("2026-05-26T03:00:00Z"),
                Instant.parse("2026-05-26T03:30:00Z")
        ));

        mockMvc.perform(get("/api/events")
                        .param("from", "2026-05-26T01:00:00Z")
                        .param("to", "2026-05-26T03:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("Boundary start"))
                .andExpect(jsonPath("$[1].title").value("Middle"))
                .andExpect(jsonPath("$[2].title").value("Boundary end"));
    }

    @Test
    void listEventsReturnsInvalidTimeRangeWhenFromIsAfterTo() throws Exception {
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-05-26T04:00:00Z")
                        .param("to", "2026-05-26T03:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.message").value("from must be earlier than or equal to to"));
    }
}
