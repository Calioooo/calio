package com.calio.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calio-context;MODE=MySQL;DATABASE_TO_UPPER=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class CalioApplicationTests {

    @Test
    void contextLoads() {
    }

}

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final OffsetDateTime START_AT = OffsetDateTime.parse("2026-06-01T10:00:00Z");
    private static final OffsetDateTime END_AT = OffsetDateTime.parse("2026-06-01T11:00:00Z");

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void createsTimedEventWhenStartAtIsEarlierThanEndAt() {
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> savedEvent(invocation.getArgument(0), 1L));

        EventResponse response = eventService.createEvent(
                new CreateEventRequest("회의", "주간 동기화", START_AT, END_AT)
        );

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("회의");
        assertThat(response.description()).isEqualTo("주간 동기화");
        assertThat(response.startAt()).isEqualTo(START_AT);
        assertThat(response.endAt()).isEqualTo(END_AT);
    }

    @Test
    void rejectsCreateWhenStartAtIsEqualToEndAt() {
        CreateEventRequest request = new CreateEventRequest("회의", null, START_AT, START_AT);

        assertThatThrownBy(() -> eventService.createEvent(request))
                .isInstanceOf(InvalidTimeRangeException.class);
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void getsPersistedEventById() {
        Event event = savedEvent(new Event("회의", null, START_AT, END_AT), 1L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        EventResponse response = eventService.getEvent(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("회의");
    }

    @Test
    void raisesEventNotFoundWhenIdDoesNotExist() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent(99L))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void listsEventsUsingInclusiveRepositoryRange() {
        OffsetDateTime from = OffsetDateTime.parse("2026-06-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-06-02T00:00:00Z");
        Event event = savedEvent(new Event("회의", null, START_AT, END_AT), 1L);
        when(eventRepository.findByStartAtBetweenOrderByStartAtAsc(from, to)).thenReturn(List.of(event));

        List<EventResponse> responses = eventService.listEvents(from, to);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(1L);
    }

    @Test
    void rejectsListRangeWhenFromIsAfterTo() {
        OffsetDateTime from = OffsetDateTime.parse("2026-06-02T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-06-01T00:00:00Z");

        assertThatThrownBy(() -> eventService.listEvents(from, to))
                .isInstanceOf(ValidationFailedException.class);
        verify(eventRepository, never()).findByStartAtBetweenOrderByStartAtAsc(any(), any());
    }

    private Event savedEvent(Event event, Long id) {
        ReflectionTestUtils.setField(event, "id", id);
        ReflectionTestUtils.setField(event, "createdAt", Instant.parse("2026-05-31T10:00:00Z"));
        ReflectionTestUtils.setField(event, "updatedAt", Instant.parse("2026-05-31T10:00:00Z"));
        return event;
    }
}

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calio-repository;MODE=MySQL;DATABASE_TO_UPPER=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    @Test
    void findsEventsByInclusiveStartAtRangeOrderedByStartAtAscending() {
        OffsetDateTime from = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        OffsetDateTime middle = OffsetDateTime.parse("2026-06-01T11:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-06-01T12:00:00Z");

        Event startsAtTo = eventRepository.save(new Event("끝 경계", null, to, to.plusHours(1)));
        Event startsBeforeRange = eventRepository.save(new Event("범위 밖", null, from.minusMinutes(1), from));
        Event startsAtFrom = eventRepository.save(new Event("시작 경계", null, from, from.plusHours(1)));
        Event startsInRange = eventRepository.save(new Event("중간", null, middle, middle.plusHours(1)));
        eventRepository.flush();

        List<Event> events = eventRepository.findByStartAtBetweenOrderByStartAtAsc(from, to);

        assertThat(events)
                .extracting(Event::getId)
                .containsExactly(startsAtFrom.getId(), startsInRange.getId(), startsAtTo.getId());
        assertThat(events).noneMatch(event -> event.getId().equals(startsBeforeRange.getId()));
    }
}

class EventControllerTest {

    private EventService eventService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        eventService = Mockito.mock(EventService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EventController(eventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsEvent() throws Exception {
        OffsetDateTime startAt = OffsetDateTime.parse("2026-06-01T10:00:01Z");
        OffsetDateTime endAt = OffsetDateTime.parse("2026-06-01T11:00:02Z");
        when(eventService.createEvent(any(CreateEventRequest.class)))
                .thenReturn(new EventResponse(1L, "회의", null, startAt, endAt, auditAt(), auditAt()));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "회의",
                                  "description": null,
                                  "startAt": "2026-06-01T10:00:01Z",
                                  "endAt": "2026-06-01T11:00:02Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("회의")))
                .andExpect(jsonPath("$.startAt", is("2026-06-01T10:00:01Z")))
                .andExpect(jsonPath("$.endAt", is("2026-06-01T11:00:02Z")));
    }

    @Test
    void returnsValidationFailedForBlankTitle() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "startAt": "2026-06-01T10:00:00Z",
                                  "endAt": "2026-06-01T11:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.message", is("Validation failed.")));
    }

    @Test
    void returnsValidationFailedForMalformedTimestamp() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "회의",
                                  "startAt": "not-a-timestamp",
                                  "endAt": "2026-06-01T11:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.message", is("Validation failed.")));
    }

    @Test
    void returnsInvalidTimeRangeWhenServiceRejectsCreateRange() throws Exception {
        when(eventService.createEvent(any(CreateEventRequest.class))).thenThrow(new InvalidTimeRangeException());

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "회의",
                                  "startAt": "2026-06-01T10:00:00Z",
                                  "endAt": "2026-06-01T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_TIME_RANGE")))
                .andExpect(jsonPath("$.message", is("startAt must be earlier than endAt.")));
    }

    @Test
    void getsEventById() throws Exception {
        OffsetDateTime startAt = OffsetDateTime.parse("2026-06-01T10:00:00Z");
        OffsetDateTime endAt = OffsetDateTime.parse("2026-06-01T11:00:00Z");
        when(eventService.getEvent(1L))
                .thenReturn(new EventResponse(1L, "회의", null, startAt, endAt, auditAt(), auditAt()));

        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("회의")));
    }

    @Test
    void returnsEventNotFoundWhenIdDoesNotExist() throws Exception {
        when(eventService.getEvent(99L)).thenThrow(new EventNotFoundException(99L));

        mockMvc.perform(get("/api/events/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("EVENT_NOT_FOUND")));
    }

    @Test
    void listsEventsByRange() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2026-06-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-06-02T00:00:00Z");
        OffsetDateTime startAt = OffsetDateTime.parse("2026-06-01T10:00:01Z");
        OffsetDateTime endAt = OffsetDateTime.parse("2026-06-01T11:00:02Z");
        when(eventService.listEvents(eq(from), eq(to)))
                .thenReturn(List.of(new EventResponse(1L, "회의", null, startAt, endAt, auditAt(), auditAt())));

        mockMvc.perform(get("/api/events")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].title", is("회의")));
    }

    private Instant auditAt() {
        return Instant.parse("2026-05-31T10:00:00Z");
    }
}
