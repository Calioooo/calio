package com.calio.calendar.recurrence.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class RecurrenceEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RecurrenceEventRepository recurrenceEventRepository;

    @Autowired
    private RecurrenceEventOverrideRepository overrideRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TagRepository tagRepository;

    private Long accountId;

    @BeforeEach
    void setUpDefaultTag() {
        accountId = currentAccountReference().getId();
        tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(TagType.PERSONAL_DEFAULT, "기타")
                .orElseGet(() -> tagRepository.save(Tag.personalDefault("기타", "#64748B")));
    }

    @Test
    @DisplayName("timed master는 timezone과 RFC line을 왕복하고 occurrence를 Event row 없이 전개한다")
    void givenTimedMaster_whenCreateDetailAndList_thenReturnsCanonicalContract() throws Exception {
        // given, when
        long recurrenceId = createTimedRecurrence("Daily", "2026-08-01", "Asia/Seoul");

        // then
        assertThat(eventRepository.findByRecurrenceIdAndAccount_IdOrderByStartAtAsc(recurrenceId, accountId))
                .isEmpty();
        mockMvc.perform(get("/api/recurrence-events/{id}", recurrenceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Daily"))
                .andExpect(jsonPath("$.allDay").value(false))
                .andExpect(jsonPath("$.firstOccurrenceStartAt").value("2026-08-01T00:00:00Z"))
                .andExpect(jsonPath("$.firstOccurrenceEndAt").value("2026-08-01T01:00:00Z"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.recurrence[0]").value("RRULE:FREQ=DAILY;COUNT=3"))
                .andExpect(jsonPath("$.canUpdateSeries").value(true))
                .andExpect(jsonPath("$.tag.title").value("기타"));

        mockMvc.perform(get("/api/events")
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-08-04T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].startAt").value("2026-08-01T00:00:00Z"))
                .andExpect(jsonPath("$[0].allDay").value(false))
                .andExpect(jsonPath("$[0].recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$[0].isRecurrenceOccurrence").value(true))
                .andExpect(jsonPath("$[2].originStartAt").value("2026-08-03T00:00:00Z"));
    }

    @Test
    @DisplayName("all-day master는 exclusive 날짜 범위를 UTC midnight occurrence로 반환한다")
    void givenAllDayMaster_whenList_thenReturnsExclusiveUtcMidnightRange() throws Exception {
        // given
        MvcResult result = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Offsite",
                                  "allDay": true,
                                  "firstOccurrenceStartAt": "2026-09-01T00:00:00Z",
                                  "firstOccurrenceEndAt": "2026-09-03T00:00:00Z",
                                  "timeZone": null,
                                  "recurrence": ["RRULE:FREQ=DAILY;COUNT=2"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allDay").value(true))
                .andExpect(jsonPath("$.firstOccurrenceStartAt").value("2026-09-01T00:00:00Z"))
                .andExpect(jsonPath("$.firstOccurrenceEndAt").value("2026-09-03T00:00:00Z"))
                .andReturn();
        long recurrenceId = readResponse(result).get("recurrenceId").asLong();

        // when, then
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-09-01T00:00:00Z")
                        .param("to", "2026-09-05T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].allDay").value(true))
                .andExpect(jsonPath("$[0].startAt").value("2026-09-01T00:00:00Z"))
                .andExpect(jsonPath("$[0].endAt").value("2026-09-03T00:00:00Z"))
                .andExpect(jsonPath("$[1].recurrenceId").value(recurrenceId));
    }

    @Test
    @DisplayName("all-day 반복은 첫 occurrence 종료 이후에도 RRULE에 따라 조회된다")
    void givenAllDayCountRule_whenListAfterFirstOccurrenceEnd_thenReturnsOccurrence() throws Exception {
        // given
        MvcResult result = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Daily all-day",
                                  "allDay": true,
                                  "firstOccurrenceStartAt": "2026-09-01T00:00:00Z",
                                  "firstOccurrenceEndAt": "2026-09-02T00:00:00Z",
                                  "timeZone": null,
                                  "recurrence": ["RRULE:FREQ=DAILY;COUNT=10"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long recurrenceId = readResponse(result).get("recurrenceId").asLong();

        // when, then
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-09-05T00:00:00Z")
                        .param("to", "2026-09-06T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$[0].startAt").value("2026-09-05T00:00:00Z"))
                .andExpect(jsonPath("$[0].endAt").value("2026-09-06T00:00:00Z"));
    }

    @Test
    @DisplayName("반복 조회가 occurrence 상한을 초과하면 안정적인 errorCode로 응답한다")
    void givenDenseRule_whenListEvents_thenReturnsOccurrenceLimitExceeded() throws Exception {
        // given
        mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Dense",
                                  "allDay": false,
                                  "firstOccurrenceStartAt": "2026-09-01T09:00:00Z",
                                  "firstOccurrenceEndAt": "2026-09-01T10:00:00Z",
                                  "timeZone": "UTC",
                                  "recurrence": ["RRULE:FREQ=SECONDLY"]
                                }
                                """))
                .andExpect(status().isCreated());

        // when, then
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-09-01T09:00:00Z")
                        .param("to", "2026-09-01T13:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    @DisplayName("active override는 null description을 포함한 snapshot 전체로 원본을 대체하고 이동 후 범위로 조회된다")
    void givenMovedOverride_whenList_thenUsesFinalSnapshotOverlap() throws Exception {
        // given
        long recurrenceId = createTimedRecurrence("Master", "2026-10-01", "UTC");
        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2026-10-01T09:00:00Z",
                                  "title": "Moved",
                                  "description": null,
                                  "startAt": "2026-11-01T12:00:00Z",
                                  "endAt": "2026-11-01T13:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Moved"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.allDay").value(false))
                .andExpect(jsonPath("$.timeZone").value("UTC"))
                .andExpect(jsonPath("$.originStartAt").value("2026-10-01T09:00:00Z"));

        // when, then
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-11-01T00:00:00Z")
                        .param("to", "2026-11-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Moved"))
                .andExpect(jsonPath("$[0].startAt").value("2026-11-01T12:00:00Z"));

        mockMvc.perform(get("/api/events")
                        .param("from", "2026-10-01T00:00:00Z")
                        .param("to", "2026-10-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("all-day master의 occurrence를 독립된 timed snapshot으로 변경할 수 있다")
    void givenAllDayMaster_whenPatchTimedOccurrence_thenUsesRequestScheduleTypeAndTimeZone()
            throws Exception {
        // given
        MvcResult createResult = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "All-day master",
                                  "allDay": true,
                                  "firstOccurrenceStartAt": "2026-10-01T00:00:00Z",
                                  "firstOccurrenceEndAt": "2026-10-02T00:00:00Z",
                                  "timeZone": null,
                                  "recurrence": ["RRULE:FREQ=DAILY;COUNT=2"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long recurrenceId = readResponse(createResult).get("recurrenceId").asLong();

        // when, then
        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2026-10-01T00:00:00Z",
                                  "title": "Timed override",
                                  "description": null,
                                  "startAt": "2026-10-01T09:00:00Z",
                                  "endAt": "2026-10-01T10:00:00Z",
                                  "allDay": false,
                                  "timeZone": "Asia/Seoul"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allDay").value(false))
                .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"));
    }

    @Test
    @DisplayName("occurrence PATCH에서 allDay를 누락하면 snapshot을 저장하지 않는다")
    void givenMissingAllDay_whenPatchOccurrence_thenReturnsValidationFailedWithoutOverride()
            throws Exception {
        // given
        long recurrenceId = createTimedRecurrence("Required allDay", "2026-10-01", "UTC");

        // when, then
        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2026-10-01T09:00:00Z",
                                  "title": "Missing allDay",
                                  "startAt": "2026-10-01T12:00:00Z",
                                  "endAt": "2026-10-01T13:00:00Z",
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                recurrenceId,
                Instant.parse("2026-10-01T09:00:00Z")
        )).isEmpty();
    }

    @Test
    @DisplayName("새 virtual occurrence와 orphan active override는 final start 순서로 exact once 조회된다")
    void givenVirtualOccurrencesAndOrphanOverride_whenList_thenReturnsExactOnceInStartOrder() throws Exception {
        // given
        long recurrenceId = createTimedRecurrence("Original rule", "2029-08-01", "UTC");
        Instant orphanOrigin = Instant.parse("2029-08-01T09:00:00Z");
        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2029-08-01T09:00:00Z",
                                  "title": "Orphan override",
                                  "description": null,
                                  "startAt": "2029-08-10T11:00:00Z",
                                  "endAt": "2029-08-10T12:00:00Z",
                                  "allDay": false,
                                  "timeZone": "Asia/Seoul"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/recurrence-events/{id}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timedRequest("Current rule", "2029-08-10", "UTC")))
                .andExpect(status().isOk());

        // when
        MvcResult result = mockMvc.perform(get("/api/events")
                        .param("from", "2029-08-10T00:00:00Z")
                        .param("to", "2029-08-12T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].startAt").value("2029-08-10T09:00:00Z"))
                .andExpect(jsonPath("$[0].originStartAt").value("2029-08-10T09:00:00Z"))
                .andExpect(jsonPath("$[0].timeZone").value("UTC"))
                .andExpect(jsonPath("$[1].startAt").value("2029-08-10T11:00:00Z"))
                .andExpect(jsonPath("$[1].originStartAt").value(orphanOrigin.toString()))
                .andExpect(jsonPath("$[1].timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$[2].startAt").value("2029-08-11T09:00:00Z"))
                .andExpect(jsonPath("$[2].timeZone").value("UTC"))
                .andReturn();

        // then
        JsonNode events = readResponse(result);
        int orphanMatches = 0;
        for (JsonNode event : events) {
            if (orphanOrigin.toString().equals(event.get("originStartAt").asText())) {
                orphanMatches++;
            }
        }
        assertThat(orphanMatches).isEqualTo(1);
        mockMvc.perform(get("/api/events")
                        .param("from", "2029-08-10T09:00:00Z")
                        .param("to", "2029-08-11T09:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].startAt").value("2029-08-10T09:00:00Z"))
                .andExpect(jsonPath("$[1].startAt").value("2029-08-10T11:00:00Z"));
    }

    @Test
    @DisplayName("전체 master 수정은 active와 deleted override 및 legacy Event를 보존하고 orphan 조회를 유지한다")
    void givenActiveAndDeletedOverrides_whenReplaceMaster_thenPreservesChildStateAndOrphanQuery() throws Exception {
        // given
        long recurrenceId = createTimedRecurrence("Master", "2026-12-01", "UTC");
        Instant activeOrigin = Instant.parse("2026-12-01T09:00:00Z");
        Instant deletedOrigin = Instant.parse("2026-12-02T09:00:00Z");
        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2026-12-01T09:00:00Z",
                                  "title": "Preserved active",
                                  "description": "snapshot memo",
                                  "startAt": "2026-12-10T12:00:00Z",
                                  "endAt": "2026-12-10T13:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .param("originStartAt", deletedOrigin.toString()))
                .andExpect(status().isNoContent());
        Instant deletedAt = overrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, deletedOrigin)
                .orElseThrow()
                .getDeletedAt();
        Tag originalTag = recurrenceEventRepository.findById(recurrenceId).orElseThrow().getTag();
        Event legacyEvent = eventRepository.save(new Event(
                "Legacy",
                null,
                Instant.parse("2026-12-20T09:00:00Z"),
                Instant.parse("2026-12-20T10:00:00Z"),
                false,
                "UTC",
                recurrenceId,
                originalTag,
                accountRepository.getReferenceById(accountId)
        ));
        Tag replacementTag = tagRepository.save(Tag.personalCustom(
                accountRepository.getReferenceById(accountId),
                "Changed tag",
                "#123456"
        ));

        // when
        mockMvc.perform(put("/api/recurrence-events/{id}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "All-day replacement",
                                  "description": "new master memo",
                                  "allDay": true,
                                  "firstOccurrenceStartAt": "2027-01-01T00:00:00Z",
                                  "firstOccurrenceEndAt": "2027-01-02T00:00:00Z",
                                  "timeZone": null,
                                  "recurrence": ["RRULE:FREQ=DAILY;COUNT=2"],
                                  "tagId": %d
                                }
                                """.formatted(replacementTag.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allDay").value(true))
                .andExpect(jsonPath("$.timeZone").doesNotExist());

        // then
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, activeOrigin))
                .hasValueSatisfying(override -> {
                    assertThat(override.getRecurrenceId()).isEqualTo(recurrenceId);
                    assertThat(override.getOriginStartAt()).isEqualTo(activeOrigin);
                    assertThat(override.getOverrideTitle()).isEqualTo("Preserved active");
                    assertThat(override.getOverrideDescription()).isEqualTo("snapshot memo");
                    assertThat(override.getOverrideStartAt()).isEqualTo(Instant.parse("2026-12-10T12:00:00Z"));
                    assertThat(override.getOverrideEndAt()).isEqualTo(Instant.parse("2026-12-10T13:00:00Z"));
                    assertThat(override.isOverrideAllDay()).isFalse();
                    assertThat(override.getOverrideTimeZone()).isEqualTo("UTC");
                    assertThat(override.getDeletedAt()).isNull();
                });
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, deletedOrigin))
                .hasValueSatisfying(override -> {
                    assertThat(override.getRecurrenceId()).isEqualTo(recurrenceId);
                    assertThat(override.getOriginStartAt()).isEqualTo(deletedOrigin);
                    assertThat(override.getOverrideTitle()).isNull();
                    assertThat(override.getOverrideStartAt()).isNull();
                    assertThat(override.getOverrideEndAt()).isNull();
                    assertThat(override.getOverrideTimeZone()).isNull();
                    assertThat(override.getDeletedAt()).isEqualTo(deletedAt);
                });
        assertThat(eventRepository.findById(legacyEvent.getId())).isPresent();

        mockMvc.perform(get("/api/events")
                        .param("from", "2026-12-10T12:00:00Z")
                        .param("to", "2026-12-10T13:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originStartAt").value(activeOrigin.toString()))
                .andExpect(jsonPath("$[0].title").value("Preserved active"))
                .andExpect(jsonPath("$[0].tag.title").value("Changed tag"));
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-12-10T11:00:00Z")
                        .param("to", "2026-12-10T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-12-10T13:00:00Z")
                        .param("to", "2026-12-10T14:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/events")
                        .param("from", "2026-12-02T09:00:00Z")
                        .param("to", "2026-12-02T10:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2026-12-01T09:00:00Z",
                                  "title": "Re-edited orphan",
                                  "description": null,
                                  "startAt": "2027-03-01T00:00:00Z",
                                  "endAt": "2027-03-02T00:00:00Z",
                                  "allDay": true,
                                  "timeZone": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allDay").value(true))
                .andExpect(jsonPath("$.tag.title").value("Changed tag"));
        assertCurrentAllDaySnapshot(recurrenceId, activeOrigin, "Re-edited orphan");

        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2026-12-02T09:00:00Z",
                                  "title": "Restored orphan",
                                  "description": "restored memo",
                                  "startAt": "2027-03-03T00:00:00Z",
                                  "endAt": "2027-03-04T00:00:00Z",
                                  "allDay": true,
                                  "timeZone": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allDay").value(true));
        assertCurrentAllDaySnapshot(recurrenceId, deletedOrigin, "Restored orphan");
    }

    @Test
    @DisplayName("잘못된 master schedule, timezone, recurrence rule은 master와 override를 변경하지 않는다")
    void givenInvalidMasterUpdates_whenReplaceMaster_thenPreservesMasterAndOverrideState() throws Exception {
        // given
        long recurrenceId = createTimedRecurrence("Validation master", "2028-01-01", "UTC");
        Instant originStartAt = Instant.parse("2028-01-01T09:00:00Z");
        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2028-01-01T09:00:00Z",
                                  "title": "Stable override",
                                  "description": "stable memo",
                                  "startAt": "2028-01-10T12:00:00Z",
                                  "endAt": "2028-01-10T13:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isOk());

        // when
        mockMvc.perform(put("/api/recurrence-events/{id}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Invalid schedule",
                                  "allDay": false,
                                  "firstOccurrenceStartAt": "2028-02-01T10:00:00Z",
                                  "firstOccurrenceEndAt": "2028-02-01T09:00:00Z",
                                  "timeZone": "UTC",
                                  "recurrence": ["RRULE:FREQ=DAILY;COUNT=2"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_RECURRENCE_SCHEDULE"));
        mockMvc.perform(put("/api/recurrence-events/{id}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timedRequest("Invalid timezone", "2028-02-01", "Mars/Olympus")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_TIME_ZONE"));
        mockMvc.perform(put("/api/recurrence-events/{id}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timedRequest("Invalid rule", "2028-02-01", "UTC")
                                .replace("RRULE:FREQ=DAILY;COUNT=3", "VEVENT:BAD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_RECURRENCE_RULE"));

        // then
        RecurrenceEvent unchangedMaster = recurrenceEventRepository.findById(recurrenceId).orElseThrow();
        assertThat(unchangedMaster.getTitle()).isEqualTo("Validation master");
        assertThat(unchangedMaster.getFirstOccurrenceStartAt()).isEqualTo(originStartAt);
        assertThat(unchangedMaster.getFirstOccurrenceEndAt()).isEqualTo(Instant.parse("2028-01-01T10:00:00Z"));
        assertThat(unchangedMaster.getTimeZone()).isEqualTo("UTC");
        assertThat(unchangedMaster.getRecurrenceRules()).containsExactly("RRULE:FREQ=DAILY;COUNT=3");
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, originStartAt))
                .hasValueSatisfying(override -> {
                    assertThat(override.getOverrideTitle()).isEqualTo("Stable override");
                    assertThat(override.getOverrideDescription()).isEqualTo("stable memo");
                    assertThat(override.getOverrideStartAt()).isEqualTo(Instant.parse("2028-01-10T12:00:00Z"));
                    assertThat(override.getOverrideEndAt()).isEqualTo(Instant.parse("2028-01-10T13:00:00Z"));
                    assertThat(override.isOverrideAllDay()).isFalse();
                    assertThat(override.getOverrideTimeZone()).isEqualTo("UTC");
                    assertThat(override.getDeletedAt()).isNull();
                });
    }

    @Test
    @DisplayName("같은 originStartAt의 PATCH와 DELETE는 한 override row에서 active와 deleted 상태를 전환한다")
    void givenSameOccurrence_whenPatchAndDeleteRepeatedly_thenTransitionsSingleOverrideState() throws Exception {
        // given
        long recurrenceId = createTimedRecurrence("State", "2027-01-01", "UTC");
        String originStartAt = "2027-01-01T09:00:00Z";
        mockMvc.perform(delete("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .param("originStartAt", originStartAt))
                .andExpect(status().isNoContent());

        // when
        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2027-01-01T09:00:00Z",
                                  "title": "Restored",
                                  "description": null,
                                  "startAt": "2027-01-02T12:00:00Z",
                                  "endAt": "2027-01-02T13:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Restored"));

        // then
        java.time.Instant origin = java.time.Instant.parse(originStartAt);
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, origin))
                .hasValueSatisfying(override -> {
                    assertThat(override.isDeleted()).isFalse();
                    assertThat(override.getOverrideTitle()).isEqualTo("Restored");
                    assertThat(override.getOverrideDescription()).isNull();
                });

        mockMvc.perform(delete("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .param("originStartAt", originStartAt))
                .andExpect(status().isNoContent());
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, origin))
                .hasValueSatisfying(override -> {
                    assertThat(override.isDeleted()).isTrue();
                    assertThat(override.getOverrideTitle()).isNull();
                });
    }

    @Test
    @DisplayName("engine이 생성하지 않는 originStartAt의 PATCH는 RECURRENCE_OCCURRENCE_NOT_FOUND를 반환한다")
    void givenUnknownOriginStartAt_whenPatchOccurrence_thenReturnsOccurrenceNotFound() throws Exception {
        // given
        long recurrenceId = createTimedRecurrence("Origin", "2027-02-01", "UTC");
        long overrideCount = overrideRepository.count();

        // when
        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2027-02-01T09:00:01Z",
                                  "title": "Unknown",
                                  "description": null,
                                  "startAt": "2027-02-01T12:00:00Z",
                                  "endAt": "2027-02-01T13:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("RECURRENCE_OCCURRENCE_NOT_FOUND"));
        mockMvc.perform(delete("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .param("originStartAt", "2027-02-01T09:00:01Z"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("RECURRENCE_OCCURRENCE_NOT_FOUND"));

        // then
        assertThat(overrideRepository.count()).isEqualTo(overrideCount);
    }

    @Test
    @DisplayName("다른 account의 실제 override identity도 master 소유권 경로에서 격리한다")
    void givenOtherAccountOverride_whenMutate_thenReturnsRecurrenceNotFoundWithoutStateChange() throws Exception {
        // given
        Account otherAccount = accountRepository.save(new Account());
        Tag defaultTag = tagRepository
                .findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(TagType.PERSONAL_DEFAULT, "기타")
                .orElseThrow();
        RecurrenceEvent otherMaster = recurrenceEventRepository.save(new RecurrenceEvent(
                "Other",
                null,
                RecurrenceSchedule.create(
                        false,
                        java.time.Instant.parse("2027-03-01T09:00:00Z"),
                        java.time.Instant.parse("2027-03-01T10:00:00Z"),
                        "UTC"
                ),
                List.of("RRULE:FREQ=DAILY;COUNT=2"),
                defaultTag,
                otherAccount
        ));
        Instant originStartAt = Instant.parse("2027-03-01T09:00:00Z");
        overrideRepository.save(RecurrenceEventOverride.active(
                otherMaster,
                originStartAt,
                "Private override",
                null,
                com.calio.calendar.common.domain.CanonicalSchedule.recurrenceOverride(
                        Instant.parse("2027-03-02T12:00:00Z"),
                        Instant.parse("2027-03-02T13:00:00Z"),
                        false,
                        "UTC"
                )
        ));

        // when
        mockMvc.perform(get("/api/recurrence-events/{id}", otherMaster.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("RECURRENCE_EVENT_NOT_FOUND"));
        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", otherMaster.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2027-03-01T09:00:00Z",
                                  "title": "Leaked",
                                  "description": null,
                                  "startAt": "2027-03-05T12:00:00Z",
                                  "endAt": "2027-03-05T13:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("RECURRENCE_EVENT_NOT_FOUND"));
        mockMvc.perform(delete("/api/recurrence-events/{id}/occurrences", otherMaster.getId())
                        .param("originStartAt", originStartAt.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("RECURRENCE_EVENT_NOT_FOUND"));

        // then
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                otherMaster.getId(),
                originStartAt
        ))
                .hasValueSatisfying(override -> {
                    assertThat(override.getOverrideTitle()).isEqualTo("Private override");
                    assertThat(override.isDeleted()).isFalse();
                });
    }

    @Test
    @DisplayName("전체 recurrence 삭제는 active와 deleted override, account legacy Event, master를 모두 제거한다")
    void givenRecurrenceChildren_whenDeleteMaster_thenRemovesAllChildrenAndMaster() throws Exception {
        // given
        long recurrenceId = createTimedRecurrence("Delete all", "2027-05-01", "UTC");
        Instant activeOrigin = Instant.parse("2027-05-01T09:00:00Z");
        Instant deletedOrigin = Instant.parse("2027-05-02T09:00:00Z");
        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2027-05-01T09:00:00Z",
                                  "title": "Active child",
                                  "description": null,
                                  "startAt": "2027-05-10T12:00:00Z",
                                  "endAt": "2027-05-10T13:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .param("originStartAt", deletedOrigin.toString()))
                .andExpect(status().isNoContent());
        RecurrenceEvent master = recurrenceEventRepository.findById(recurrenceId).orElseThrow();
        Event legacyEvent = eventRepository.save(new Event(
                "Legacy child",
                null,
                Instant.parse("2027-05-20T09:00:00Z"),
                Instant.parse("2027-05-20T10:00:00Z"),
                false,
                "UTC",
                recurrenceId,
                master.getTag(),
                accountRepository.getReferenceById(accountId)
        ));

        // when
        mockMvc.perform(delete("/api/recurrence-events/{id}", recurrenceId))
                .andExpect(status().isNoContent());

        // then
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, activeOrigin))
                .isEmpty();
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, deletedOrigin))
                .isEmpty();
        assertThat(eventRepository.findById(legacyEvent.getId())).isEmpty();
        assertThat(recurrenceEventRepository.findById(recurrenceId)).isEmpty();
    }

    @Test
    @DisplayName("다른 account의 custom tag로 recurrence master를 생성하면 TAG_NOT_FOUND를 반환한다")
    void givenOtherAccountTag_whenCreate_thenReturnsTagNotFound() throws Exception {
        // given
        Account otherAccount = accountRepository.save(new Account());
        Tag otherTag = tagRepository.save(Tag.personalCustom(otherAccount, "Other", "#123456"));

        // when, then
        mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timedRequest("Owned", "2027-04-01", "UTC")
                                .replace(
                                        "\"recurrence\": [\"RRULE:FREQ=DAILY;COUNT=3\"]",
                                        "\"recurrence\": [\"RRULE:FREQ=DAILY;COUNT=3\"],"
                                                + System.lineSeparator()
                                                + "  \"tagId\": " + otherTag.getId()
                                )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("TAG_NOT_FOUND"));
    }

    @Test
    @DisplayName("schedule, timezone, RFC line 오류는 각각 안정적인 ProblemDetail errorCode로 응답한다")
    void givenInvalidDefinitions_whenCreate_thenMapsContractErrors() throws Exception {
        mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timedRequest("Bad zone", "2026-08-01", "Mars/Olympus")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_TIME_ZONE"));

        mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Bad schedule",
                                  "allDay": true,
                                  "firstOccurrenceStartAt": "2026-08-01T00:00:00Z",
                                  "firstOccurrenceEndAt": "2026-08-01T00:00:00Z",
                                  "recurrence": ["RRULE:FREQ=DAILY"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_RECURRENCE_SCHEDULE"));

        mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timedRequest("Bad rule", "2026-08-01", "UTC")
                                .replace("RRULE:FREQ=DAILY;COUNT=3", "VEVENT:BAD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_RECURRENCE_RULE"));

        mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timedRequest("Empty rule", "2026-08-01", "UTC")
                                .replace(
                                        "\"recurrence\": [\"RRULE:FREQ=DAILY;COUNT=3\"]",
                                        "\"recurrence\": []"
                                )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_RECURRENCE_RULE"));
    }

    @Test
    @DisplayName("RDATE, EXDATE, EXRULE, 복수 RRULE 입력을 하나의 recurrence set으로 저장한다")
    void givenMultipleRecurrenceRules_whenCreate_thenAcceptsContract() throws Exception {
        // given
        String request = timedRequest("Multiple rules", "2026-08-01", "UTC");
        String recurrence = "\"recurrence\": [\"RRULE:FREQ=DAILY;COUNT=3\"]";
        String multipleLines = """
                "recurrence": [
                    "RRULE:FREQ=DAILY;COUNT=3",
                    "RRULE:FREQ=WEEKLY;COUNT=2;BYDAY=MO",
                    "RDATE:20260805T090000Z",
                    "EXDATE:20260802T090000Z",
                    "EXRULE:FREQ=WEEKLY;COUNT=2;BYDAY=TU"
                  ]""";

        // when, then
        mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace(recurrence, multipleLines)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrence", hasSize(5)));
    }

    private long createTimedRecurrence(String title, String date, String timeZone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timedRequest(title, date, timeZone)))
                .andExpect(status().isCreated())
                .andReturn();
        return readResponse(result).get("recurrenceId").asLong();
    }

    private void assertCurrentAllDaySnapshot(Long recurrenceId, Instant originStartAt, String title) {
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, originStartAt))
                .hasValueSatisfying(override -> {
                    assertThat(override.getOriginStartAt()).isEqualTo(originStartAt);
                    assertThat(override.getOverrideTitle()).isEqualTo(title);
                    assertThat(override.isOverrideAllDay()).isTrue();
                    assertThat(override.getOverrideTimeZone()).isNull();
                    assertThat(override.getDeletedAt()).isNull();
                });
    }

    private String timedRequest(String title, String date, String timeZone) {
        ZoneId scheduleZone;
        try {
            scheduleZone = ZoneId.of(timeZone);
        } catch (java.time.DateTimeException exception) {
            scheduleZone = ZoneOffset.UTC;
        }
        LocalDateTime localStart = LocalDate.parse(date).atTime(9, 0);
        Instant startAt = localStart.atZone(scheduleZone).toInstant();
        Instant endAt = localStart.plusHours(1).atZone(scheduleZone).toInstant();
        return """
                {
                  "title": "%s",
                  "description": "memo",
                  "allDay": false,
                  "firstOccurrenceStartAt": "%s",
                  "firstOccurrenceEndAt": "%s",
                  "timeZone": "%s",
                  "recurrence": ["RRULE:FREQ=DAILY;COUNT=3"]
                }
                """.formatted(title, startAt, endAt, timeZone);
    }

    private JsonNode readResponse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
