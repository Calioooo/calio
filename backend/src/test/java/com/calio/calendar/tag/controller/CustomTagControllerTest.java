package com.calio.calendar.tag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.calio.calendar.security.TestAccountSupport.currentAccountReference;

import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.repository.TagRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceFrequency;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-custom-tag-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class CustomTagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RecurrenceEventRepository recurrenceEventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        recurrenceEventRepository.deleteAll();
        tagRepository.deleteAll();
    }

    @Test
    @DisplayName("사용자는 custom tag를 생성하면 colorCode가 대문자로 정규화된 CUSTOM 응답을 받는다")
    void givenValidCustomTagRequest_whenCreateCustomTag_thenReturnsCreatedCustomTag() throws Exception {
        // when
        mockMvc.perform(post("/api/custom-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customTagRequest("운동", "#22c55e")))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("운동"))
                .andExpect(jsonPath("$.colorCode").value("#22C55E"))
                .andExpect(jsonPath("$.tagType").value("CUSTOM"))
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.*", hasSize(4)));
    }

    @Test
    @DisplayName("custom tag 제목은 중복될 수 있다")
    void givenDuplicateTitle_whenCreateCustomTags_thenStoresBothTags() throws Exception {
        // when
        mockMvc.perform(post("/api/custom-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customTagRequest("중복", "#111111")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/custom-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customTagRequest("중복", "#222222")))
                .andExpect(status().isCreated());

        // then
        long duplicateTitleCount = tagRepository.findAll().stream()
                .filter(tag -> tag.getTagType() == TagType.CUSTOM)
                .filter(tag -> tag.getTitle().equals("중복"))
                .count();
        assertThat(duplicateTitleCount).isEqualTo(2);
    }

    @Test
    @DisplayName("custom tag 생성은 공백 제목을 VALIDATION_FAILED로 거부한다")
    void givenBlankTitle_whenCreateCustomTag_thenReturnsValidationFailed() throws Exception {
        // when
        mockMvc.perform(post("/api/custom-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customTagRequest(" ", "#22C55E")))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("custom tag 생성은 공백 colorCode를 VALIDATION_FAILED로 거부한다")
    void givenBlankColorCode_whenCreateCustomTag_thenReturnsValidationFailed() throws Exception {
        // when
        mockMvc.perform(post("/api/custom-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customTagRequest("색상 공백", " ")))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("custom tag 생성은 잘못된 colorCode를 INVALID_TAG_COLOR_CODE로 거부한다")
    void givenInvalidColorCode_whenCreateCustomTag_thenReturnsInvalidTagColorCode() throws Exception {
        // when
        mockMvc.perform(post("/api/custom-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customTagRequest("색상 오류", "22C55E")))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_TAG_COLOR_CODE"));
    }

    @Test
    @DisplayName("사용자는 custom tag만 수정할 수 있고 DEFAULT tagId는 TAG_NOT_FOUND를 받는다")
    void givenCustomAndDefaultTagIds_whenUpdateCustomTag_thenOnlyCustomTagIsUpdated() throws Exception {
        // given
        Tag customTag = tagRepository.save(new Tag(TagType.CUSTOM, "기존", "#111111", currentAccountReference()));
        Tag defaultTag = tagRepository.save(new Tag(TagType.DEFAULT, "기타", "#64748B"));

        // when
        mockMvc.perform(put("/api/custom-tags/{tagId}", customTag.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customTagRequest("변경", "#abcdef")))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customTag.getId()))
                .andExpect(jsonPath("$.title").value("변경"))
                .andExpect(jsonPath("$.colorCode").value("#ABCDEF"))
                .andExpect(jsonPath("$.tagType").value("CUSTOM"));

        mockMvc.perform(put("/api/custom-tags/{tagId}", defaultTag.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customTagRequest("기본 수정", "#000000")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("TAG_NOT_FOUND"));
    }

    @Test
    @DisplayName("custom tag 삭제는 ordinary event, recurrence rule, occurrence event를 fallback 기타 태그로 재할당한다")
    void givenCustomTagInUse_whenDeleteCustomTag_thenReassignsAllUsagesToFallbackTag() throws Exception {
        // given
        Tag fallbackTag = tagRepository.save(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        Tag customTag = tagRepository.save(new Tag(TagType.CUSTOM, "삭제 대상", "#8B5CF6", currentAccountReference()));
        Event ordinaryEvent = eventRepository.save(event("일반", null, customTag));
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.save(recurrenceEvent(customTag));
        Event occurrenceEvent = eventRepository.save(event("반복 occurrence", recurrenceEvent.getId(), customTag));

        // when
        MvcResult deleteResult = mockMvc.perform(delete("/api/custom-tags/{tagId}", customTag.getId()))
                // then
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(deleteResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(tagRepository.existsById(customTag.getId())).isFalse();
        assertThat(eventRepository.findById(ordinaryEvent.getId()))
                .hasValueSatisfying(event -> assertThat(event.getTag().getId()).isEqualTo(fallbackTag.getId()));
        assertThat(eventRepository.findById(occurrenceEvent.getId()))
                .hasValueSatisfying(event -> assertThat(event.getTag().getId()).isEqualTo(fallbackTag.getId()));
        assertThat(recurrenceEventRepository.findById(recurrenceEvent.getId()))
                .hasValueSatisfying(rule -> assertThat(rule.getTag().getId()).isEqualTo(fallbackTag.getId()));
    }

    @Test
    @DisplayName("fallback 기타 태그가 없으면 custom tag 삭제는 내부 오류를 노출하지 않고 기존 참조를 보존한다")
    void givenMissingFallbackTag_whenDeleteCustomTag_thenHidesInternalErrorAndKeepsReferences()
            throws Exception {
        // given
        Tag customTag = tagRepository.save(new Tag(TagType.CUSTOM, "삭제 보류", "#8B5CF6", currentAccountReference()));
        Event ordinaryEvent = eventRepository.save(event("일반", null, customTag));
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.save(recurrenceEvent(customTag));

        // when
        mockMvc.perform(delete("/api/custom-tags/{tagId}", customTag.getId()))
                // then
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.errorCode").doesNotExist());

        assertThat(tagRepository.existsById(customTag.getId())).isTrue();
        assertThat(eventRepository.findById(ordinaryEvent.getId()))
                .hasValueSatisfying(event -> assertThat(event.getTag().getId()).isEqualTo(customTag.getId()));
        assertThat(recurrenceEventRepository.findById(recurrenceEvent.getId()))
                .hasValueSatisfying(rule -> assertThat(rule.getTag().getId()).isEqualTo(customTag.getId()));
    }

    private String customTagRequest(String title, String colorCode) {
        return """
                {
                  "title": "%s",
                  "colorCode": "%s"
                }
                """.formatted(title, colorCode);
    }

    private Event event(String title, Long recurrenceId, Tag tag) {
        return new Event(
                title,
                null,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T01:00:00Z"),
                recurrenceId,
                tag,
                currentAccountReference()
        );
    }

    private RecurrenceEvent recurrenceEvent(Tag tag) {
        return new RecurrenceEvent(
                "반복",
                null,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-02"),
                LocalTime.parse("09:00:00"),
                LocalTime.parse("10:00:00"),
                RecurrenceFrequency.DAILY,
                tag,
                currentAccountReference()
        );
    }
}
