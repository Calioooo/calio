package com.calio.calendar.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.calio.calendar.controller.dto.CreateEventRequest;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.entity.Event;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    @Test
    void createEventReturnsPersistedEventWithAuditFields() throws Exception {
        CreateEventRequest request = new CreateEventRequest(
                "Design review",
                "Calendar API review",
                Instant.parse("2026-05-27T09:00:00Z"),
                Instant.parse("2026-05-27T10:00:00Z")
        );

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Design review"))
                .andExpect(jsonPath("$.description").value("Calendar API review"))
                .andExpect(jsonPath("$.startAt").value("2026-05-27T09:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2026-05-27T10:00:00Z"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createEventRejectsBlankTitleWithValidationFailed() throws Exception {
        CreateEventRequest request = new CreateEventRequest(
                "   ",
                null,
                Instant.parse("2026-05-27T09:00:00Z"),
                Instant.parse("2026-05-27T10:00:00Z")
        );

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("title: must not be blank"));
    }

    @Test
    void createEventRejectsNonAscendingTimesWithInvalidTimeRange() throws Exception {
        CreateEventRequest request = new CreateEventRequest(
                "Back-to-back",
                null,
                Instant.parse("2026-05-27T10:00:00Z"),
                Instant.parse("2026-05-27T10:00:00Z")
        );

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.message").value("startAt must be earlier than endAt"));
    }

    @Test
    void getEventReturnsSavedEvent() throws Exception {
        Event savedEvent = eventRepository.saveAndFlush(new Event(
                "1:1",
                "Weekly sync",
                Instant.parse("2026-05-27T13:00:00Z"),
                Instant.parse("2026-05-27T13:30:00Z")
        ));

        mockMvc.perform(get("/api/events/{eventId}", savedEvent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEvent.getId()))
                .andExpect(jsonPath("$.title").value("1:1"))
                .andExpect(jsonPath("$.description").value("Weekly sync"))
                .andExpect(jsonPath("$.startAt").value("2026-05-27T13:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2026-05-27T13:30:00Z"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void getEventReturnsEventNotFoundForMissingId() throws Exception {
        mockMvc.perform(get("/api/events/{eventId}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Event not found for id 9999"));
    }

    @Test
    void listEventsReturnsInclusiveBoundaryMatchesOrderedByStartAtAscending() throws Exception {
        eventRepository.saveAndFlush(new Event(
                "Before range",
                null,
                Instant.parse("2026-05-27T07:59:59Z"),
                Instant.parse("2026-05-27T08:30:00Z")
        ));
        eventRepository.saveAndFlush(new Event(
                "Starts at from",
                null,
                Instant.parse("2026-05-27T08:00:00Z"),
                Instant.parse("2026-05-27T08:30:00Z")
        ));
        eventRepository.saveAndFlush(new Event(
                "Middle slot",
                null,
                Instant.parse("2026-05-27T09:00:00Z"),
                Instant.parse("2026-05-27T09:30:00Z")
        ));
        eventRepository.saveAndFlush(new Event(
                "Starts at to",
                null,
                Instant.parse("2026-05-27T10:00:00Z"),
                Instant.parse("2026-05-27T10:30:00Z")
        ));
        eventRepository.saveAndFlush(new Event(
                "After range",
                null,
                Instant.parse("2026-05-27T10:00:01Z"),
                Instant.parse("2026-05-27T10:30:00Z")
        ));

        mockMvc.perform(get("/api/events")
                        .param("from", "2026-05-27T08:00:00Z")
                        .param("to", "2026-05-27T10:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].title").value("Starts at from"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$[1].title").value("Middle slot"))
                .andExpect(jsonPath("$[2].title").value("Starts at to"));
    }

    @Test
    void listEventsRejectsDescendingQueryRangeWithInvalidTimeRange() throws Exception {
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-05-27T10:00:00Z")
                        .param("to", "2026-05-27T08:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.message").value("from must be earlier than or equal to to"));
    }
}
