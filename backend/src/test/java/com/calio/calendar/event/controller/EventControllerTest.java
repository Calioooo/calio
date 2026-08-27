package com.calio.calendar.event.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.calio.calendar.security.TestAccountSupport.currentAccountReference;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.tag.repository.TagRepository;
import com.calio.calendar.account.domain.Account;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository;

    @Autowired
    private GoogleCalendarEventMappingRepository googleCalendarEventMappingRepository;

    @BeforeEach
    void setUpDefaultTag() {
        tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(TagType.PERSONAL_DEFAULT, "기타")
                .orElseGet(() -> tagRepository.save(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B")));
    }

    @Test
    @DisplayName("사용자는 단일 시간 일정을 생성하면 서버가 생성한 감사 필드가 포함된 일정을 받는다")
    void givenValidEventRequest_whenCreateEvent_thenReturnsPersistedEventWithServerManagedAuditFields()
            throws Exception {
        // given
        String requestBody = """
                {
                  "title": "Planning",
                  "description": "Weekly planning",
                  "startAt": "2026-06-01T00:00:00Z",
                  "endAt": "2026-06-01T01:00:00Z",
                  "allDay": false,
                  "timeZone": "UTC",
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
                .andExpect(jsonPath("$.startAt").value("2026-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2026-06-01T01:00:00Z"))
                .andExpect(jsonPath("$.allDay").value(false))
                .andExpect(jsonPath("$.timeZone").value("UTC"))
                .andExpect(jsonPath("$.importantEvent").value(false))
                .andExpect(jsonPath("$.tag.title").value("기타"))
                .andExpect(jsonPath("$.tag.colorCode").value("#64748B"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andReturn();

        JsonNode response = readResponse(result);
        assertThat(response.get("createdAt").asString()).isNotEqualTo("2000-01-01T00:00:00Z");
        assertThat(response.get("updatedAt").asString()).isNotEqualTo("2000-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("일정 생성 요청에서 allDay를 누락하면 VALIDATION_FAILED를 반환한다")
    void givenMissingAllDay_whenCreateEvent_thenReturnsValidationFailed() throws Exception {
        // when, then
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Missing allDay",
                                  "startAt": "2026-06-01T00:00:00Z",
                                  "endAt": "2026-06-01T01:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("개인 일정 다중 그룹 공유의 DTO 검증 오류는 기존 Problem JSON 계약을 유지한다")
    void givenInvalidGroupShareRequest_whenCreateGroupShares_thenReturnsValidationProblem() throws Exception {
        mockMvc.perform(post("/api/events/group-shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventIds": [],
                                  "groupSpaceIds": [null]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed."))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.instance").value("/api/events/group-shares"))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("UTC 자정과 exclusive end로 생성한 종일 일정은 저장된 allDay=true를 반환한다")
    void givenUtcMidnightExclusiveRange_whenCreateAllDayEvent_thenReturnsStoredAllDay() throws Exception {
        // when, then
        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "All day",
                                  "startAt": "2026-06-10T00:00:00Z",
                                  "endAt": "2026-06-12T00:00:00Z",
                                  "allDay": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startAt").value("2026-06-10T00:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2026-06-12T00:00:00Z"))
                .andExpect(jsonPath("$.allDay").value(true))
                .andReturn();
        assertThat(readResponse(result).get("timeZone").isNull()).isTrue();
    }

    @Test
    @DisplayName("timed 일정은 non-blank valid IANA timeZone 없이는 생성할 수 없다")
    void givenMissingOrInvalidTimeZone_whenCreateTimedEvent_thenReturnsInvalidTimeZone()
            throws Exception {
        // when, then
        for (String timeZoneField : new String[]{"", ", \"timeZone\": \"\"", ", \"timeZone\": \"Invalid/Zone\""}) {
            mockMvc.perform(post("/api/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "Invalid timed zone",
                                      "startAt": "2026-06-10T09:00:00Z",
                                      "endAt": "2026-06-10T10:00:00Z",
                                      "allDay": false%s
                                    }
                                    """.formatted(timeZoneField)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("INVALID_TIME_ZONE"));
        }
    }

    @Test
    @DisplayName("all-day 일정에 timeZone이 있으면 INVALID_ALL_DAY_SCHEDULE을 반환한다")
    void givenTimeZone_whenCreateAllDayEvent_thenReturnsInvalidAllDaySchedule()
            throws Exception {
        // when, then
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Invalid all-day zone",
                                  "startAt": "2026-06-10T00:00:00Z",
                                  "endAt": "2026-06-11T00:00:00Z",
                                  "allDay": true,
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_ALL_DAY_SCHEDULE"));
    }

    @Test
    @DisplayName("종일 일정은 UTC 자정이 아닌 경계를 허용하지 않는다")
    void givenNonMidnightBoundary_whenCreateAllDayEvent_thenReturnsInvalidAllDaySchedule()
            throws Exception {
        // when, then
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Invalid all day",
                                  "startAt": "2026-06-10T01:00:00Z",
                                  "endAt": "2026-06-11T01:00:00Z",
                                  "allDay": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_ALL_DAY_SCHEDULE"));
    }

    @Test
    @DisplayName("PUT은 timed 일정을 all-day 일정으로 전환하고 저장값을 반환한다")
    void givenTimedEvent_whenUpdateToAllDay_thenReplacesCanonicalSchedule() throws Exception {
        // given
        long eventId = createEvent(
                "Timed",
                "2026-06-13T09:00:00Z",
                "2026-06-13T10:00:00Z"
        );

        // when, then
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Now all day",
                                  "startAt": "2026-06-14T00:00:00Z",
                                  "endAt": "2026-06-15T00:00:00Z",
                                  "allDay": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allDay").value(true))
                .andExpect(jsonPath("$.startAt").value("2026-06-14T00:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2026-06-15T00:00:00Z"));
    }

    @Test
    @DisplayName("사용자는 PERSONAL_DEFAULT tagId를 지정해 일정을 생성하면 해당 태그가 저장된 응답을 받는다")
    void givenPersonalDefaultTagId_whenCreateEvent_thenStoresSelectedTag() throws Exception {
        // given
        Tag workTag = tagRepository.save(new Tag(TagType.PERSONAL_DEFAULT, "업무", "#2563eb"));

        // when
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Tagged event",
                                  "startAt": "2026-06-16T00:00:00Z",
                                  "endAt": "2026-06-16T01:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC",
                                  "tagId": %d
                                }
                                """.formatted(workTag.getId())))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tag.id").value(workTag.getId()))
                .andExpect(jsonPath("$.tag.title").value("업무"))
                .andExpect(jsonPath("$.tag.colorCode").value("#2563EB"))
                .andExpect(jsonPath("$.tag.tagType").value("PERSONAL_DEFAULT"));
    }

    @Test
    @DisplayName("사용자는 CUSTOM tagId를 지정해 일정을 생성하면 해당 태그가 저장된 응답을 받는다")
    void givenCustomTagId_whenCreateEvent_thenStoresSelectedTag() throws Exception {
        // given
        Tag customTag = tagRepository.save(new Tag(TagType.CUSTOM, "사용자", "#111111", currentAccountReference()));

        // when
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Custom tag rejected",
                                  "startAt": "2026-06-17T00:00:00Z",
                                  "endAt": "2026-06-17T01:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC",
                                  "tagId": %d
                                }
                                """.formatted(customTag.getId())))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tag.id").value(customTag.getId()))
                .andExpect(jsonPath("$.tag.title").value("사용자"))
                .andExpect(jsonPath("$.tag.colorCode").value("#111111"))
                .andExpect(jsonPath("$.tag.tagType").value("CUSTOM"));
    }

    @Test
    @DisplayName("사용자는 tagId 없이 일정을 수정하면 fallback PERSONAL_DEFAULT 기타 태그로 변경된다")
    void givenNullTagId_whenUpdateEvent_thenChangesToFallbackTag() throws Exception {
        // given
        Tag workTag = tagRepository.save(new Tag(TagType.PERSONAL_DEFAULT, "업무 수정", "#2563EB"));
        MvcResult createResult = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Change to fallback",
                                  "startAt": "2026-06-18T00:00:00Z",
                                  "endAt": "2026-06-18T01:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC",
                                  "tagId": %d
                                }
                                """.formatted(workTag.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        long eventId = readResponse(createResult).get("id").asLong();

        // when
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Fallback tag",
                                  "startAt": "2026-06-18T02:00:00Z",
                                  "endAt": "2026-06-18T03:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC",
                                  "tagId": null
                                }
                                """))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag.title").value("기타"))
                .andExpect(jsonPath("$.tag.colorCode").value("#64748B"));
    }

    @Test
    @DisplayName("사용자는 CUSTOM tagId를 지정해 일정을 수정하면 해당 태그가 저장된 응답을 받는다")
    void givenCustomTagId_whenUpdateEvent_thenStoresSelectedTag() throws Exception {
        // given
        Tag customTag = tagRepository.save(new Tag(TagType.CUSTOM, "수정 사용자", "#8b5cf6", currentAccountReference()));
        long eventId = createEvent("Custom update target", "2026-06-19T00:00:00Z", "2026-06-19T01:00:00Z");

        // when
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Custom updated",
                                  "startAt": "2026-06-19T02:00:00Z",
                                  "endAt": "2026-06-19T03:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC",
                                  "tagId": %d
                                }
                                """.formatted(customTag.getId())))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag.id").value(customTag.getId()))
                .andExpect(jsonPath("$.tag.title").value("수정 사용자"))
                .andExpect(jsonPath("$.tag.colorCode").value("#8B5CF6"))
                .andExpect(jsonPath("$.tag.tagType").value("CUSTOM"));
    }

    @Test
    @DisplayName("존재하지 않는 tagId로 일정을 생성하면 TAG_NOT_FOUND를 받는다")
    void givenMissingTagId_whenCreateEvent_thenReturnsTagNotFound() throws Exception {
        // when
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Missing tag",
                                  "startAt": "2026-06-20T00:00:00Z",
                                  "endAt": "2026-06-20T01:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC",
                                  "tagId": 999999
                                }
                                """))
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("TAG_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 Account의 CUSTOM tagId로 일정을 생성하면 TAG_NOT_FOUND를 받는다")
    void givenOtherAccountCustomTagId_whenCreateEvent_thenReturnsTagNotFound() throws Exception {
        // given
        Account otherAccount = accountRepository.saveAndFlush(new Account());
        Tag otherAccountTag = tagRepository.save(
                new Tag(TagType.CUSTOM, "다른 계정", "#111111", otherAccount)
        );

        // when
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Other account tag",
                                  "startAt": "2026-06-20T00:00:00Z",
                                  "endAt": "2026-06-20T01:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC",
                                  "tagId": %d
                                }
                                """.formatted(otherAccountTag.getId())))
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("TAG_NOT_FOUND"));
    }

    @Test
    @DisplayName("사용자는 공백 제목으로 일정을 생성할 수 없다")
    void givenBlankTitle_whenCreateEvent_thenReturnsValidationFailed() throws Exception {
        // given
        String requestBody = """
                {
                  "title": " ",
                  "startAt": "2026-06-01T00:00:00Z",
                  "endAt": "2026-06-01T01:00:00Z",
                  "allDay": false,
                  "timeZone": "UTC"
                }
                """;

        // when
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    @DisplayName("사용자는 시작 시각이 종료 시각보다 빠르지 않은 일정을 생성할 수 없다")
    void givenStartAtIsNotEarlierThanEndAt_whenCreateEvent_thenReturnsInvalidTimeRange() throws Exception {
        // given
        String requestBody = """
                {
                  "title": "Planning",
                  "startAt": "2026-06-01T01:00:00Z",
                  "endAt": "2026-06-01T01:00:00Z",
                  "allDay": false,
                  "timeZone": "UTC"
                }
                """;

        // when
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    @DisplayName("사용자는 생성된 일정 id로 단일 일정을 조회할 수 있다")
    void givenExistingEventId_whenGetEvent_thenReturnsEvent() throws Exception {
        // given
        long eventId = createEvent("Review", "2026-06-02T00:00:00Z", "2026-06-02T01:00:00Z");

        // when
        mockMvc.perform(get("/api/events/{eventId}", eventId))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.title").value("Review"))
                .andExpect(jsonPath("$.importantEvent").value(false));
    }

    @Test
    @DisplayName("사용자는 일정을 중요 일정으로 등록하고 해제할 수 있다")
    void givenExistingEvent_whenUpdateImportantEvent_thenReturnsUpdatedImportantEventState()
            throws Exception {
        // given
        long eventId = createEvent("Important target", "2026-06-11T00:00:00Z", "2026-06-11T01:00:00Z");

        // when, then
        updateImportantEventResult(eventId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.importantEvent").value(true));

        updateImportantEventResult(eventId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.importantEvent").value(false));
    }

    @Test
    @DisplayName("사용자는 같은 중요 일정 상태를 반복 요청해도 성공 응답을 받는다")
    void givenSameImportantEventState_whenUpdateImportantEventTwice_thenReturnsIdempotentSuccess()
            throws Exception {
        // given
        long eventId = createEvent("Idempotent target", "2026-06-12T00:00:00Z", "2026-06-12T01:00:00Z");

        // when, then
        updateImportantEventResult(eventId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importantEvent").value(true));

        updateImportantEventResult(eventId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importantEvent").value(true));

        updateImportantEventResult(eventId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importantEvent").value(false));

        updateImportantEventResult(eventId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importantEvent").value(false));
    }

    @Test
    @DisplayName("요청에 중요 일정 여부가 없으면 상태를 변경할 수 없다")
    void givenMissingImportantEventField_whenUpdateImportantEvent_thenReturnsValidationFailed()
            throws Exception {
        // given
        long eventId = createEvent("Missing important field", "2026-06-13T00:00:00Z", "2026-06-13T01:00:00Z");

        // when
        mockMvc.perform(patch("/api/events/{eventId}/important-event", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("요청에 boolean이 아닌 값으로 중요 일정 여부를 전달할 수 없다")
    void givenNonBooleanImportantEventField_whenUpdateImportantEvent_thenReturnsValidationFailed()
            throws Exception {
        // given
        long eventId = createEvent("Invalid important field", "2026-06-14T00:00:00Z", "2026-06-14T01:00:00Z");

        // when
        mockMvc.perform(patch("/api/events/{eventId}/important-event", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "importantEvent": "yes"
                                }
                                """))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("사용자는 존재하지 않는 일정 id의 중요 일정 상태를 변경하면 EVENT_NOT_FOUND를 받는다")
    void givenMissingEventId_whenUpdateImportantEvent_thenReturnsEventNotFound() throws Exception {
        // given
        long missingEventId = 999999L;

        // when
        updateImportantEventResult(missingEventId, true)
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("사용자는 중요 일정 변경 후 단일 조회에서 저장된 중요 일정 상태를 받는다")
    void givenChangedImportantEvent_whenGetEvent_thenReturnsStoredImportantEventState()
            throws Exception {
        // given
        long eventId = createEvent("Read important", "2026-06-15T00:00:00Z", "2026-06-15T01:00:00Z");
        updateImportantEventResult(eventId, true)
                .andExpect(status().isOk());

        // when
        mockMvc.perform(get("/api/events/{eventId}", eventId))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.importantEvent").value(true));
    }

    @Test
    @DisplayName("사용자는 기존 일정을 전체 교체하면 id와 생성 시각은 보존되고 변경 가능한 필드만 바뀐다")
    void givenExistingEvent_whenUpdateEvent_thenReplacesMutableFieldsAndPreservesServerManagedFields()
            throws Exception {
        // given
        MvcResult createResult = createEventResult(
                "Original",
                "2026-06-04T00:00:00Z",
                "2026-06-04T01:00:00Z"
        );
        JsonNode createdEvent = readResponse(createResult);
        long eventId = createdEvent.get("id").asLong();
        MvcResult persistedResult = mockMvc.perform(get("/api/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andReturn();
        String persistedCreatedAt = readResponse(persistedResult).get("createdAt").asString();

        String requestBody = """
                {
                  "id": 999999,
                  "title": "Updated",
                  "description": null,
                  "startAt": "2026-06-04T02:00:00Z",
                  "endAt": "2026-06-04T03:00:00Z",
                  "allDay": false,
                  "timeZone": "UTC",
                  "createdAt": "2000-01-01T00:00:00Z",
                  "updatedAt": "2000-01-01T00:00:00Z",
                  "unknown": "ignored"
                }
                """;

        // when
        MvcResult updateResult = mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.title").value("Updated"))
                .andExpect(jsonPath("$.startAt").value("2026-06-04T02:00:00Z"))
                .andExpect(jsonPath("$.endAt").value("2026-06-04T03:00:00Z"))
                .andExpect(jsonPath("$.createdAt").value(persistedCreatedAt))
                .andExpect(jsonPath("$.updatedAt").isString())
                .andReturn();

        JsonNode updatedEvent = readResponse(updateResult);
        assertThat(updatedEvent.get("description").isNull()).isTrue();
        assertThat(updatedEvent.get("updatedAt").asString()).isNotEqualTo("2000-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("사용자는 공백 제목으로 일정을 수정할 수 없다")
    void givenBlankTitle_whenUpdateEvent_thenReturnsValidationFailed() throws Exception {
        // given
        long eventId = createEvent("Editable", "2026-06-05T00:00:00Z", "2026-06-05T01:00:00Z");
        String requestBody = """
                {
                  "title": " ",
                  "startAt": "2026-06-05T02:00:00Z",
                  "endAt": "2026-06-05T03:00:00Z",
                  "allDay": false,
                  "timeZone": "UTC"
                }
                """;

        // when
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("사용자는 필수 시각 없이 일정을 수정할 수 없다")
    void givenMissingRequiredTimeFields_whenUpdateEvent_thenReturnsValidationFailed() throws Exception {
        // given
        long eventId = createEvent("Editable", "2026-06-06T00:00:00Z", "2026-06-06T01:00:00Z");

        // when, then
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated",
                                  "endAt": "2026-06-06T03:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.*", hasSize(6)));

        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated",
                                  "startAt": "2026-06-06T02:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("사용자는 종일 일정 여부 없이 일정을 수정할 수 없다")
    void givenMissingAllDay_whenUpdateEvent_thenReturnsValidationFailed() throws Exception {
        // given
        long eventId = createEvent(
                "Editable",
                "2026-06-06T00:00:00Z",
                "2026-06-06T01:00:00Z"
        );

        // when, then
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated",
                                  "startAt": "2026-06-06T02:00:00Z",
                                  "endAt": "2026-06-06T03:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("사용자는 시작 시각이 종료 시각보다 빠르지 않게 일정을 수정할 수 없다")
    void givenStartAtIsNotEarlierThanEndAt_whenUpdateEvent_thenReturnsInvalidTimeRange() throws Exception {
        // given
        long eventId = createEvent("Editable", "2026-06-07T00:00:00Z", "2026-06-07T01:00:00Z");
        String requestBody = """
                {
                  "title": "Updated",
                  "startAt": "2026-06-07T02:00:00Z",
                  "endAt": "2026-06-07T02:00:00Z",
                  "allDay": false,
                  "timeZone": "UTC"
                }
                """;

        // when
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("잘못된 timed timezone 수정은 Event 필드와 Tag를 부분 변경하지 않는다")
    void givenInvalidTimeZoneAndNewTag_whenUpdateEvent_thenPreservesEventAndTag()
            throws Exception {
        // given
        long eventId = createEvent("Stable", "2026-06-07T00:00:00Z", "2026-06-07T01:00:00Z");
        Event before = eventRepository.findById(eventId).orElseThrow();
        Long originalTagId = before.getTag().getId();
        Tag replacementTag = tagRepository.save(new Tag(TagType.PERSONAL_DEFAULT, "교체 대상", "#123456"));

        // when
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Changed",
                                  "description": "partial mutation",
                                  "startAt": "2026-06-07T02:00:00Z",
                                  "endAt": "2026-06-07T03:00:00Z",
                                  "allDay": false,
                                  "timeZone": "Invalid/Zone",
                                  "tagId": %d
                                }
                                """.formatted(replacementTag.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_TIME_ZONE"));

        // then
        Event persisted = eventRepository.findById(eventId).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Stable");
        assertThat(persisted.getStartAt()).isEqualTo(Instant.parse("2026-06-07T00:00:00Z"));
        assertThat(persisted.getEndAt()).isEqualTo(Instant.parse("2026-06-07T01:00:00Z"));
        assertThat(persisted.getTimeZone()).isEqualTo("UTC");
        assertThat(persisted.getTag().getId()).isEqualTo(originalTagId);
    }

    @Test
    @DisplayName("사용자는 존재하지 않는 일정 id를 수정하면 EVENT_NOT_FOUND를 받는다")
    void givenMissingEventId_whenUpdateEvent_thenReturnsEventNotFound() throws Exception {
        // given
        long missingEventId = 999999L;
        String requestBody = """
                {
                  "title": "Updated",
                  "startAt": "2026-06-08T00:00:00Z",
                  "endAt": "2026-06-08T01:00:00Z",
                  "allDay": false,
                  "timeZone": "UTC"
                }
                """;

        // when
        mockMvc.perform(put("/api/events/{eventId}", missingEventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.*", hasSize(6)));
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
                .andExpect(jsonPath("$.title").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    @DisplayName("사용자는 일정을 삭제하면 본문 없는 204를 받고 기존 조회 API에서 삭제된 일정을 볼 수 없다")
    void givenExistingEvent_whenDeleteEvent_thenReturnsNoContentAndEventIsHiddenFromReads() throws Exception {
        // given
        long deletedEventId = createEvent("Delete me", "2026-06-09T00:00:00Z", "2026-06-09T01:00:00Z");
        long remainingEventId = createEvent("Keep me", "2026-06-09T02:00:00Z", "2026-06-09T03:00:00Z");

        // when
        MvcResult deleteResult = mockMvc.perform(delete("/api/events/{eventId}", deletedEventId))
                // then
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(deleteResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEmpty();

        mockMvc.perform(get("/api/events/{eventId}", deletedEventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("EVENT_NOT_FOUND"));

        MvcResult listResult = mockMvc.perform(get("/api/events")
                        .param("from", "2026-06-09T00:00:00Z")
                        .param("to", "2026-06-09T03:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(remainingEventId))
                .andReturn();
        JsonNode events = readResponse(listResult);
        assertThat(containsEventId(events, deletedEventId)).isFalse();
    }

    @Test
    @DisplayName("Google mapping 일정은 모든 변경 요청을 차단한다")
    void givenGoogleMappedEvent_whenMutate_thenAppliesExternalMutationPolicy() throws Exception {
        // given
        long eventId = createEvent(
                "Google import",
                "2026-06-21T00:00:00Z",
                "2026-06-21T01:00:00Z"
        );
        mapAsGoogleEvent(eventId);

        // when, then
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Blocked update",
                                  "startAt": "2026-06-21T02:00:00Z",
                                  "endAt": "2026-06-21T03:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED"));

        mockMvc.perform(delete("/api/events/{eventId}", eventId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED"));

        updateImportantEventResult(eventId, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED"));
    }

    @Test
    @DisplayName("사용자는 존재하지 않거나 이미 삭제된 일정 id를 삭제하면 EVENT_NOT_FOUND를 받는다")
    void givenMissingOrAlreadyDeletedEventId_whenDeleteEvent_thenReturnsEventNotFound() throws Exception {
        // given
        long eventId = createEvent("Delete once", "2026-06-10T00:00:00Z", "2026-06-10T01:00:00Z");
        long missingEventId = 999999L;
        mockMvc.perform(delete("/api/events/{eventId}", eventId))
                .andExpect(status().isNoContent());

        // when, then
        mockMvc.perform(delete("/api/events/{eventId}", missingEventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.*", hasSize(6)));

        mockMvc.perform(delete("/api/events/{eventId}", eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("사용자는 표시 시간이 요청 범위와 겹치는 일정을 시작 시각 오름차순으로 조회한다")
    void givenEventsAcrossRangeBoundaries_whenListEvents_thenReturnsOverlappingEventsSortedByStartAt()
            throws Exception {
        // given
        long overlappingBeforeId = createEvent("Before", "2026-06-02T23:59:59Z", "2026-06-03T00:30:00Z");
        long lowerBoundaryId = createEvent("Lower", "2026-06-03T00:00:00Z", "2026-06-03T01:00:00Z");
        long middleId = createEvent("Middle", "2026-06-03T01:00:00Z", "2026-06-03T02:00:00Z");
        createEvent("Upper", "2026-06-03T02:00:00Z", "2026-06-03T03:00:00Z");
        createEvent("After", "2026-06-03T02:00:01Z", "2026-06-03T03:30:00Z");

        // when
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-06-03T00:00:00Z")
                        .param("to", "2026-06-03T02:00:00Z"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(overlappingBeforeId))
                .andExpect(jsonPath("$[1].id").value(lowerBoundaryId))
                .andExpect(jsonPath("$[2].id").value(middleId));
    }

    @Test
    @DisplayName("일정 조회 범위가 366일을 초과하면 요청을 거부한다")
    void givenEventQueryRangeOverLimit_whenListEvents_thenReturnsRangeTooLarge() throws Exception {
        // when
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2027-01-03T00:00:00Z"))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("EVENT_QUERY_RANGE_TOO_LARGE"))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    @DisplayName("중요 일정 상태 변경 후에도 조회 결과는 startAt 기준으로 정렬된다")
    void givenChangedImportantEvent_whenListEvents_thenReturnsStoredImportantEventStateSortedByStartAt()
            throws Exception {
        // given
        long firstId = createEvent("First", "2026-07-01T00:00:00Z", "2026-07-01T01:00:00Z");
        long importantId = createEvent("Important", "2026-07-01T01:00:00Z", "2026-07-01T02:00:00Z");
        createEvent("Last", "2026-07-01T02:00:00Z", "2026-07-01T03:00:00Z");
        updateImportantEventResult(importantId, true)
                .andExpect(status().isOk());

        // when
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-01T02:00:00Z"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(firstId))
                .andExpect(jsonPath("$[0].importantEvent").value(false))
                .andExpect(jsonPath("$[1].id").value(importantId))
                .andExpect(jsonPath("$[1].importantEvent").value(true));
    }

    private long createEvent(String title, String startAt, String endAt) throws Exception {
        return readResponse(createEventResult(title, startAt, endAt)).get("id").asLong();
    }

    private MvcResult createEventResult(String title, String startAt, String endAt) throws Exception {
        return mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "startAt": "%s",
                                  "endAt": "%s",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """.formatted(title, startAt, endAt)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private ResultActions updateImportantEventResult(long eventId, boolean importantEvent) throws Exception {
        return mockMvc.perform(patch("/api/events/{eventId}/important-event", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "importantEvent": %s
                        }
                        """.formatted(importantEvent)));
    }

    private void mapAsGoogleEvent(long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow();
        GoogleCalendarIntegration integration = googleCalendarIntegrationRepository.saveAndFlush(
                new GoogleCalendarIntegration(
                        event.getAccount().getId(),
                        "google-subject-" + eventId,
                        "user@example.com",
                        "encrypted-refresh-token",
                        "encrypted-access-token",
                        Instant.parse("2026-06-21T02:00:00Z"),
                        Instant.parse("2026-06-21T00:00:00Z")
                )
        );
        googleCalendarEventMappingRepository.saveAndFlush(new GoogleCalendarEventMapping(
                integration,
                event,
                "external-" + eventId,
                null,
                null
        ));
    }

    private JsonNode readResponse(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(content);
    }

    private boolean containsEventId(JsonNode events, long eventId) {
        for (JsonNode event : events) {
            if (event.get("id").asLong() == eventId) {
                return true;
            }
        }

        return false;
    }
}
