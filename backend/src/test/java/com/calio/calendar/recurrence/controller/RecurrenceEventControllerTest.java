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
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
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
        tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullOrderByIdAsc(TagType.DEFAULT, "기타")
                .orElseGet(() -> tagRepository.save(new Tag(TagType.DEFAULT, "기타", "#64748B")));
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
                .andExpect(jsonPath("$.startDate").value("2026-08-01"))
                .andExpect(jsonPath("$.endDate").value("2026-08-03"))
                .andExpect(jsonPath("$.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.recurrence[0]").value("RRULE:FREQ=DAILY;COUNT=3"))
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
                                  "startDate": "2026-09-01",
                                  "endDate": "2026-09-03",
                                  "startTime": null,
                                  "endTime": null,
                                  "timeZone": null,
                                  "recurrence": ["RRULE:FREQ=DAILY;COUNT=2"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allDay").value(true))
                .andExpect(jsonPath("$.startTime").doesNotExist())
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
    @DisplayName("all-day 반복은 첫 occurrence의 endDate 이후에도 RRULE에 따라 조회된다")
    void givenAllDayCountRule_whenListAfterFirstOccurrenceEnd_thenReturnsOccurrence() throws Exception {
        // given
        MvcResult result = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Daily all-day",
                                  "allDay": true,
                                  "startDate": "2026-09-01",
                                  "endDate": "2026-09-02",
                                  "startTime": null,
                                  "endTime": null,
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
                                  "startDate": "2026-09-01",
                                  "endDate": "2026-09-01",
                                  "startTime": "09:00:00",
                                  "endTime": "10:00:00",
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
                                  "endAt": "2026-11-01T13:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Moved"))
                .andExpect(jsonPath("$.description").doesNotExist())
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
    @DisplayName("DELETE override는 nullable snapshot 삭제 상태로 저장되고 전체 master 수정은 override를 제거한다")
    void givenDeletedOverride_whenReplaceMaster_thenClearsOverrideState() throws Exception {
        // given
        long recurrenceId = createTimedRecurrence("Master", "2026-12-01", "UTC");
        mockMvc.perform(delete("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .param("originStartAt", "2026-12-01T09:00:00Z"))
                .andExpect(status().isNoContent());
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                recurrenceId,
                java.time.Instant.parse("2026-12-01T09:00:00Z")
        )).hasValueSatisfying(override -> {
            assertThat(override.isDeleted()).isTrue();
            assertThat(override.getOverrideTitle()).isNull();
            assertThat(override.getOverrideStartAt()).isNull();
        });

        // when
        mockMvc.perform(put("/api/recurrence-events/{id}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timedRequest("Replaced", "2026-12-02", "UTC")))
                .andExpect(status().isOk());

        // then
        assertThat(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                recurrenceId,
                java.time.Instant.parse("2026-12-01T09:00:00Z")
        )).isEmpty();
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
                                  "endAt": "2027-01-02T13:00:00Z"
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

        // when, then
        mockMvc.perform(patch("/api/recurrence-events/{id}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2027-02-01T09:00:01Z",
                                  "title": "Unknown",
                                  "description": null,
                                  "startAt": "2027-02-01T12:00:00Z",
                                  "endAt": "2027-02-01T13:00:00Z"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("RECURRENCE_OCCURRENCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 account의 recurrence master는 RECURRENCE_EVENT_NOT_FOUND로 격리한다")
    void givenOtherAccountMaster_whenGet_thenReturnsRecurrenceNotFound() throws Exception {
        // given
        Account otherAccount = accountRepository.save(new Account());
        Tag defaultTag = tagRepository
                .findFirstByTagTypeAndTitleAndAccountIsNullOrderByIdAsc(TagType.DEFAULT, "기타")
                .orElseThrow();
        RecurrenceEvent otherMaster = recurrenceEventRepository.save(new RecurrenceEvent(
                "Other",
                null,
                RecurrenceSchedule.create(
                        false,
                        LocalDate.parse("2027-03-01"),
                        LocalDate.parse("2027-03-01"),
                        LocalTime.parse("09:00"),
                        LocalTime.parse("10:00"),
                        "UTC"
                ),
                List.of("RRULE:FREQ=DAILY;COUNT=2"),
                defaultTag,
                otherAccount
        ));

        // when, then
        mockMvc.perform(get("/api/recurrence-events/{id}", otherMaster.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("RECURRENCE_EVENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 account의 custom tag로 recurrence master를 생성하면 TAG_NOT_FOUND를 반환한다")
    void givenOtherAccountTag_whenCreate_thenReturnsTagNotFound() throws Exception {
        // given
        Account otherAccount = accountRepository.save(new Account());
        Tag otherTag = tagRepository.save(new Tag(TagType.CUSTOM, "Other", "#123456", otherAccount));

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
                                  "startDate": "2026-08-01",
                                  "endDate": "2026-08-01",
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
    @DisplayName("RDATE, EXRULE, 복수 RRULE 입력은 INVALID_RECURRENCE_RULE로 응답한다")
    void givenUnsupportedRecurrenceRules_whenCreate_thenRejectsContract() throws Exception {
        // given
        String request = timedRequest("Unsupported rule", "2026-08-01", "UTC");
        String recurrence = "\"recurrence\": [\"RRULE:FREQ=DAILY;COUNT=3\"]";
        List<String> unsupportedRecurrences = List.of(
                "\"recurrence\": [\"RRULE:FREQ=DAILY;COUNT=3\", \"RDATE:20260802T090000Z\"]",
                "\"recurrence\": [\"RRULE:FREQ=DAILY;COUNT=3\", \"EXRULE:FREQ=WEEKLY;BYDAY=SA\"]",
                "\"recurrence\": [\"RRULE:FREQ=DAILY\", \"RRULE:FREQ=WEEKLY;BYDAY=MO\"]"
        );

        // when, then
        for (String unsupportedRecurrence : unsupportedRecurrences) {
            mockMvc.perform(post("/api/recurrence-events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request.replace(recurrence, unsupportedRecurrence)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("INVALID_RECURRENCE_RULE"));
        }
    }

    private long createTimedRecurrence(String title, String date, String timeZone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timedRequest(title, date, timeZone)))
                .andExpect(status().isCreated())
                .andReturn();
        return readResponse(result).get("recurrenceId").asLong();
    }

    private String timedRequest(String title, String date, String timeZone) {
        String endDate = LocalDate.parse(date).plusDays(2).toString();
        return """
                {
                  "title": "%s",
                  "description": "memo",
                  "allDay": false,
                  "startDate": "%s",
                  "endDate": "%s",
                  "startTime": "09:00:00",
                  "endTime": "10:00:00",
                  "timeZone": "%s",
                  "recurrence": ["RRULE:FREQ=DAILY;COUNT=3"]
                }
                """.formatted(title, date, endDate, timeZone);
    }

    private JsonNode readResponse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
