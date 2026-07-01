package com.calio.calendar.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.repository.TagRepository;
import com.calio.calendar.repository.entity.Tag;
import com.calio.calendar.repository.entity.TagType;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TagRepository tagRepository;

    @BeforeEach
    void setUpDefaultTag() {
        tagRepository.findFirstByTagTypeAndTitleOrderByIdAsc(TagType.DEFAULT, "기타")
                .orElseGet(() -> tagRepository.save(new Tag(TagType.DEFAULT, "기타", "#64748B")));
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
                .andExpect(jsonPath("$.importantEvent").value(false))
                .andExpect(jsonPath("$.tag.title").value("기타"))
                .andExpect(jsonPath("$.tag.colorCode").value("#64748B"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andReturn();

        JsonNode response = readResponse(result);
        assertThat(response.get("createdAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");
        assertThat(response.get("updatedAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("사용자는 DEFAULT tagId를 지정해 일정을 생성하면 해당 태그가 저장된 응답을 받는다")
    void givenDefaultTagId_whenCreateEvent_thenStoresSelectedTag() throws Exception {
        // given
        Tag workTag = tagRepository.save(new Tag(TagType.DEFAULT, "업무", "#2563eb"));

        // when
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Tagged event",
                                  "startAt": "2026-06-16T00:00:00Z",
                                  "endAt": "2026-06-16T01:00:00Z",
                                  "tagId": %d
                                }
                                """.formatted(workTag.getId())))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tag.id").value(workTag.getId()))
                .andExpect(jsonPath("$.tag.title").value("업무"))
                .andExpect(jsonPath("$.tag.colorCode").value("#2563EB"));
    }

    @Test
    @DisplayName("사용자는 CUSTOM tagId로 일정을 생성할 수 없다")
    void givenCustomTagId_whenCreateEvent_thenReturnsTagNotFound() throws Exception {
        // given
        Tag customTag = tagRepository.save(new Tag(TagType.CUSTOM, "사용자", "#111111"));

        // when
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Custom tag rejected",
                                  "startAt": "2026-06-17T00:00:00Z",
                                  "endAt": "2026-06-17T01:00:00Z",
                                  "tagId": %d
                                }
                                """.formatted(customTag.getId())))
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TAG_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Tag not found."));
    }

    @Test
    @DisplayName("사용자는 tagId 없이 일정을 수정하면 fallback DEFAULT 기타 태그로 변경된다")
    void givenNullTagId_whenUpdateEvent_thenChangesToFallbackTag() throws Exception {
        // given
        Tag workTag = tagRepository.save(new Tag(TagType.DEFAULT, "업무 수정", "#2563EB"));
        MvcResult createResult = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Change to fallback",
                                  "startAt": "2026-06-18T00:00:00Z",
                                  "endAt": "2026-06-18T01:00:00Z",
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
                                  "tagId": null
                                }
                                """))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tag.title").value("기타"))
                .andExpect(jsonPath("$.tag.colorCode").value("#64748B"));
    }

    @Test
    @DisplayName("사용자는 공백 제목으로 일정을 생성할 수 없다")
    void givenBlankTitle_whenCreateEvent_thenReturnsValidationFailed() throws Exception {
        // given
        String requestBody = """
                {
                  "title": " ",
                  "startAt": "2026-06-01T00:00:00Z",
                  "endAt": "2026-06-01T01:00:00Z"
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
                  "startAt": "2026-06-01T01:00:00Z",
                  "endAt": "2026-06-01T01:00:00Z"
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
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
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
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
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
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
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
        String persistedCreatedAt = readResponse(persistedResult).get("createdAt").asText();

        String requestBody = """
                {
                  "id": 999999,
                  "title": "Updated",
                  "description": null,
                  "startAt": "2026-06-04T02:00:00Z",
                  "endAt": "2026-06-04T03:00:00Z",
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
        assertThat(updatedEvent.get("updatedAt").asText()).isNotEqualTo("2000-01-01T00:00:00Z");
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
                  "endAt": "2026-06-05T03:00:00Z"
                }
                """;

        // when
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
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
                                  "endAt": "2026-06-06T03:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));

        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated",
                                  "startAt": "2026-06-06T02:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
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
                  "endAt": "2026-06-07T02:00:00Z"
                }
                """;

        // when
        mockMvc.perform(put("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_RANGE"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
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
                  "endAt": "2026-06-08T01:00:00Z"
                }
                """;

        // when
        mockMvc.perform(put("/api/events/{eventId}", missingEventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
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
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_FOUND"));

        MvcResult listResult = mockMvc.perform(get("/api/events")
                        .param("from", "2026-06-09T00:00:00Z")
                        .param("to", "2026-06-09T02:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(remainingEventId))
                .andReturn();
        JsonNode events = readResponse(listResult);
        assertThat(containsEventId(events, deletedEventId)).isFalse();
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
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));

        mockMvc.perform(delete("/api/events/{eventId}", eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.*", hasSize(2)));
    }

    @Test
    @DisplayName("사용자는 시작 시각 범위에 포함되는 일정을 시작 시각 오름차순으로 조회한다")
    void givenEventsAcrossRangeBoundaries_whenListEvents_thenReturnsInclusiveRangeSortedByStartAt()
            throws Exception {
        // given
        createEvent("Before", "2026-06-02T23:59:59Z", "2026-06-03T00:30:00Z");
        long lowerBoundaryId = createEvent("Lower", "2026-06-03T00:00:00Z", "2026-06-03T01:00:00Z");
        long middleId = createEvent("Middle", "2026-06-03T01:00:00Z", "2026-06-03T02:00:00Z");
        long upperBoundaryId = createEvent("Upper", "2026-06-03T02:00:00Z", "2026-06-03T03:00:00Z");
        createEvent("After", "2026-06-03T02:00:01Z", "2026-06-03T03:30:00Z");

        // when
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-06-03T00:00:00Z")
                        .param("to", "2026-06-03T02:00:00Z"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(lowerBoundaryId))
                .andExpect(jsonPath("$[1].id").value(middleId))
                .andExpect(jsonPath("$[2].id").value(upperBoundaryId));
    }

    @Test
    @DisplayName("중요 일정 상태 변경 후에도 조회 결과는 startAt 기준으로 정렬된다")
    void givenChangedImportantEvent_whenListEvents_thenReturnsStoredImportantEventStateSortedByStartAt()
            throws Exception {
        // given
        long firstId = createEvent("First", "2026-07-01T00:00:00Z", "2026-07-01T01:00:00Z");
        long importantId = createEvent("Important", "2026-07-01T01:00:00Z", "2026-07-01T02:00:00Z");
        long lastId = createEvent("Last", "2026-07-01T02:00:00Z", "2026-07-01T03:00:00Z");
        updateImportantEventResult(importantId, true)
                .andExpect(status().isOk());

        // when
        mockMvc.perform(get("/api/events")
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-01T02:00:00Z"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(firstId))
                .andExpect(jsonPath("$[0].importantEvent").value(false))
                .andExpect(jsonPath("$[1].id").value(importantId))
                .andExpect(jsonPath("$[1].importantEvent").value(true))
                .andExpect(jsonPath("$[2].id").value(lastId))
                .andExpect(jsonPath("$[2].importantEvent").value(false));
    }

    private long createEvent(String title, String startAt, String endAt) throws Exception {
        return readResponse(createEventResult(title, startAt, endAt)).get("id").asLong();
    }

    private MvcResult createEventResult(String title, String startAt, String endAt) throws Exception {
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

        return result;
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
