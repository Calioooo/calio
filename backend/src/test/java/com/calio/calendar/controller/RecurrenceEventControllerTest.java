package com.calio.calendar.controller;

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

import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.repository.TagRepository;
import com.calio.calendar.repository.entity.RecurrenceEventOverride;
import com.calio.calendar.repository.entity.Tag;
import com.calio.calendar.repository.entity.TagType;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @Autowired
    private TagRepository tagRepository;

    private Long currentAccountId;

    @BeforeEach
    void setUpDefaultTag() {
        currentAccountId = currentAccountReference().getId();
        tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullOrderByIdAsc(TagType.DEFAULT, "기타")
                .orElseGet(() -> tagRepository.save(new Tag(TagType.DEFAULT, "기타", "#64748B")));
    }

    @Test
    @DisplayName("반복 일정 생성은 rule만 저장하고 GET /api/events에서 가상 occurrence를 반환한다")
    void givenRecurrenceRule_whenCreateAndListEvents_thenReturnsVirtualOccurrencesWithoutEventRows()
            throws Exception {
        // given, when
        long recurrenceId = createRecurrenceEvent("Daily standup", "2026-08-01", "2026-08-03", "DAILY");

        // then
        assertThat(eventRepository.findByRecurrenceIdAndAccount_IdOrderByStartAtAsc(recurrenceId, currentAccountId))
                .isEmpty();

        mockMvc.perform(get("/api/events")
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-08-04T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].title").value("Daily standup"))
                .andExpect(jsonPath("$[0].startAt").value("2026-08-01T09:00:00Z"))
                .andExpect(jsonPath("$[0].endAt").value("2026-08-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$[0].originStartAt").value("2026-08-01T09:00:00Z"))
                .andExpect(jsonPath("$[0].isRecurrenceOccurrence").value(true))
                .andExpect(jsonPath("$[1].originStartAt").value("2026-08-02T09:00:00Z"))
                .andExpect(jsonPath("$[2].originStartAt").value("2026-08-03T09:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/events는 일반 일정과 가상 recurrence occurrence를 표시 시간 기준으로 병합 정렬한다")
    void givenNormalAndRecurrenceEvents_whenListEvents_thenMergesByDisplayTime() throws Exception {
        // given
        long normalEventId = createEvent("Normal", "2026-09-01T08:30:00Z", "2026-09-01T09:30:00Z");
        long recurrenceId = createRecurrenceEvent("Daily", "2026-09-01", "2026-09-01", "DAILY");

        // when, then
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-09-01T08:00:00Z")
                        .param("to", "2026-09-01T11:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(normalEventId))
                .andExpect(jsonPath("$[0].originStartAt").doesNotExist())
                .andExpect(jsonPath("$[0].isRecurrenceOccurrence").value(false))
                .andExpect(jsonPath("$[1].id").doesNotExist())
                .andExpect(jsonPath("$[1].recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$[1].originStartAt").value("2026-09-01T09:00:00Z"));
    }

    @Test
    @DisplayName("단일 occurrence PATCH는 originStartAt 기준 modified override를 만들고 이동된 표시 시간으로 조회된다")
    void givenPatchOccurrence_whenListEvents_thenSuppressesBaseAndReturnsMovedOverride() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent("Move target", "2026-12-01", "2026-12-02", "DAILY");

        // when
        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2026-12-01T09:00:00Z",
                                  "startAt": "2026-12-10T12:00:00Z",
                                  "endAt": "2026-12-10T13:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$.originStartAt").value("2026-12-01T09:00:00Z"))
                .andExpect(jsonPath("$.startAt").value("2026-12-10T12:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2026-12-10T13:00:00Z"));

        // then
        assertThat(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                recurrenceId,
                Instant.parse("2026-12-01T09:00:00Z")
        ))
                .hasValueSatisfying(override -> {
                    assertThat(override.getOverrideStartAt()).isEqualTo(Instant.parse("2026-12-10T12:00:00Z"));
                    assertThat(override.getOverrideEndAt()).isEqualTo(Instant.parse("2026-12-10T13:00:00Z"));
                    assertThat(override.getDeletedAt()).isNull();
                });

        mockMvc.perform(get("/api/events")
                        .param("from", "2026-12-01T00:00:00Z")
                        .param("to", "2026-12-03T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originStartAt").value("2026-12-02T09:00:00Z"));

        mockMvc.perform(get("/api/events")
                        .param("from", "2026-12-10T00:00:00Z")
                        .param("to", "2026-12-11T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originStartAt").value("2026-12-01T09:00:00Z"))
                .andExpect(jsonPath("$[0].startAt").value("2026-12-10T12:00:00Z"));
    }

    @Test
    @DisplayName("단일 occurrence DELETE는 deletion override를 만들고 반복 호출해도 204를 반환한다")
    void givenDeleteOccurrenceTwice_whenListEvents_thenSuppressesOccurrenceIdempotently() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent("Delete target", "2027-01-01", "2027-01-02", "DAILY");

        // when, then
        deleteOccurrence(recurrenceId, "2027-01-01T09:00:00Z");
        deleteOccurrence(recurrenceId, "2027-01-01T09:00:00Z");

        RecurrenceEventOverride override = recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, Instant.parse("2027-01-01T09:00:00Z"))
                .orElseThrow();
        assertThat(override.getDeletedAt()).isNotNull();
        assertThat(override.getOverrideStartAt()).isNull();
        assertThat(override.getOverrideEndAt()).isNull();

        mockMvc.perform(get("/api/events")
                        .param("from", "2027-01-01T00:00:00Z")
                        .param("to", "2027-01-03T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originStartAt").value("2027-01-02T09:00:00Z"));
    }

    @Test
    @DisplayName("반복 일정 전체 PUT은 rule을 갱신하고 해당 recurrence override를 hard-delete한다")
    void givenWholeRecurrencePut_whenOverridesExist_thenUpdatesRuleAndDeletesOverrides() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent("Whole update", "2027-02-01", "2027-02-02", "DAILY");
        patchOccurrence(recurrenceId, "2027-02-01T09:00:00Z", "2027-02-10T09:00:00Z", "2027-02-10T10:00:00Z");
        assertThat(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                recurrenceId,
                Instant.parse("2027-02-01T09:00:00Z")
        )).isPresent();

        // when
        mockMvc.perform(put("/api/recurrence-events/{recurrenceId}", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated whole",
                                  "startAt": "2027-02-03T11:00:00Z",
                                  "endAt": "2027-02-10T12:00:00Z",
                                  "recurrenceFrequency": "WEEKLY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceTitle").value("Updated whole"))
                .andExpect(jsonPath("$.recurrenceStartDate").value("2027-02-03"))
                .andExpect(jsonPath("$.recurrenceEndDate").value("2027-02-10"))
                .andExpect(jsonPath("$.recurrenceFrequency").value("WEEKLY"));

        // then
        assertThat(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                recurrenceId,
                Instant.parse("2027-02-01T09:00:00Z")
        )).isEmpty();
        assertThat(eventRepository.findByRecurrenceIdAndAccount_IdOrderByStartAtAsc(recurrenceId, currentAccountId))
                .isEmpty();
    }

    @Test
    @DisplayName("단일 occurrence PATCH는 잘못된 시간 범위와 생성 불가능한 originStartAt을 지정된 errorCode로 반환한다")
    void givenInvalidOccurrencePatch_whenPatch_thenReturnsContractErrorCodes() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent("Invalid patch", "2027-03-01", "2027-03-01", "DAILY");

        // when, then
        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2027-03-01T09:00:00Z",
                                  "startAt": "2027-03-01T10:00:00Z",
                                  "endAt": "2027-03-01T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"));

        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2027-03-02T09:00:00Z",
                                  "startAt": "2027-03-02T10:00:00Z",
                                  "endAt": "2027-03-02T11:00:00Z"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECURRENCE_OCCURRENCE_NOT_FOUND"));

        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}/occurrences", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2027-03-01T09:00:00Z",
                                  "startAt": "2027-03-01T10:00:00Z",
                                  "endAt": "2027-03-01T11:00:00Z"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECURRENCE_EVENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("deleted override 대상 PATCH는 occurrence를 복원하지 않고 RECURRENCE_OCCURRENCE_NOT_FOUND를 반환한다")
    void givenDeletedOverride_whenPatchOccurrence_thenReturnsOccurrenceNotFound() throws Exception {
        // given
        long recurrenceId = createRecurrenceEvent("Deleted target", "2027-04-01", "2027-04-01", "DAILY");
        deleteOccurrence(recurrenceId, "2027-04-01T09:00:00Z");

        // when, then
        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2027-04-01T09:00:00Z",
                                  "startAt": "2027-04-01T11:00:00Z",
                                  "endAt": "2027-04-01T12:00:00Z"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECURRENCE_OCCURRENCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/events는 from이 to보다 늦으면 INVALID_TIME_RANGE를 반환한다")
    void givenInvalidListRange_whenListEvents_thenReturnsInvalidTimeRange() throws Exception {
        mockMvc.perform(get("/api/events")
                        .param("from", "2027-05-02T00:00:00Z")
                        .param("to", "2027-05-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"));
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

    private long createRecurrenceEvent(String title, String startDate, String endDate, String frequency)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurrenceRequest(title, startDate, endDate, frequency)))
                .andExpect(status().isCreated())
                .andReturn();
        return readResponse(result).get("recurrenceId").asLong();
    }

    private String recurrenceRequest(String title, String startDate, String endDate, String frequency) {
        return """
                {
                  "recurrenceTitle": "%s",
                  "recurrenceDescription": "memo",
                  "recurrenceStartDate": "%s",
                  "recurrenceEndDate": "%s",
                  "recurrenceStartTime": "09:00:00",
                  "recurrenceEndTime": "10:00:00",
                  "recurrenceFrequency": "%s"
                }
                """.formatted(title, startDate, endDate, frequency);
    }

    private void patchOccurrence(Long recurrenceId, String originStartAt, String startAt, String endAt)
            throws Exception {
        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "%s",
                                  "startAt": "%s",
                                  "endAt": "%s"
                                }
                                """.formatted(originStartAt, startAt, endAt)))
                .andExpect(status().isOk());
    }

    private void deleteOccurrence(Long recurrenceId, String originStartAt) throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/recurrence-events/{recurrenceId}/occurrences", recurrenceId)
                        .param("originStartAt", originStartAt))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEmpty();
    }

    private JsonNode readResponse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
