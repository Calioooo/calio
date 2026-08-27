package com.calio.calendar.groupcalendar.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceOverrideRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
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
        "spring.datasource.url=jdbc:h2:mem:group-calendar-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class GroupCalendarControllerTest {

    private static final Instant START_AT = Instant.parse("2026-08-01T09:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-08-01T10:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private GroupCalendarEventRepository eventRepository;
    @Autowired private GroupCalendarRecurrenceOverrideRepository recurrenceOverrideRepository;
    @Autowired private GroupCalendarRecurrenceEventRepository recurrenceEventRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupSpaceRepository groupSpaceRepository;

    @BeforeEach
    void setUp() {
        recurrenceOverrideRepository.deleteAll();
        recurrenceEventRepository.deleteAll();
        eventRepository.deleteAll();
        tagRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupSpaceRepository.deleteAll();
    }

    @Test
    @DisplayName("ACTIVE 멤버는 같은 Group Space 태그와 직접 일정을 생성·수정·조회·삭제한다")
    void givenActiveMember_whenManagingGroupTagAndEvent_thenKeepsGroupScopedApiContract() throws Exception {
        // given
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(currentAccountId(), "group", null)
        );
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace,
                currentAccountId(),
                "nickname",
                START_AT
        ));
        tagRepository.saveAndFlush(new Tag(TagType.GROUP_DEFAULT, "기타", "#64748B", groupSpace));

        // when
        MvcResult createdTag = mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/tags", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagRequest("업무", "#2563EB")))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tagType").value("CUSTOM"))
                .andReturn();
        Long tagId = responseId(createdTag);

        mockMvc.perform(patch("/api/group-spaces/{groupSpaceId}/tags/{tagId}", groupSpace.getId(), tagId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagRequest("회의", "#0EA5E9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("회의"));

        MvcResult createdEvent = mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/events", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest("회의", tagId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tag.id").value(tagId))
                .andReturn();
        Long eventId = responseId(createdEvent);

        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/events", groupSpace.getId())
                        .param("from", START_AT.minusSeconds(1).toString())
                        .param("to", END_AT.plusSeconds(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("회의"));

        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/calendar/items", groupSpace.getId())
                        .param("from", START_AT.minusSeconds(1).toString())
                        .param("to", END_AT.plusSeconds(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].creatorNickname").value("nickname"));

        mockMvc.perform(delete("/api/group-spaces/{groupSpaceId}/events/{eventId}", groupSpace.getId(), eventId))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/group-spaces/{groupSpaceId}/tags/{tagId}", groupSpace.getId(), tagId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Group Calendar API는 비멤버와 필수값이 누락된 요청을 거절한다")
    void givenNonMemberOrInvalidRequest_whenCallingCalendarApi_thenReturnsContractError() throws Exception {
        // given
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(new GroupSpace(999L, "other", null));

        // when, then
        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/calendar/items", groupSpace.getId())
                        .param("from", START_AT.toString())
                        .param("to", END_AT.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GROUP_SPACE_NOT_FOUND"));
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/events", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/recurrence-events", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("통합 Group Calendar 응답은 직접 일정과 반복 회차의 JSON 구분 키를 유지한다")
    void givenDirectAndRecurringEvents_whenListCalendarItems_thenSerializesOccurrenceKey() throws Exception {
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(currentAccountId(), "group", null)
        );
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace, currentAccountId(), "nickname", START_AT
        ));
        Tag defaultTag = tagRepository.saveAndFlush(Tag.groupDefault(groupSpace));

        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/events", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest("직접 일정", defaultTag.getId())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/recurrence-events", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurrenceRequest("반복 일정", defaultTag.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/calendar/items", groupSpace.getId())
                        .param("from", START_AT.minusSeconds(1).toString())
                        .param("to", END_AT.plusSeconds(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].isRecurrenceOccurrence").value(false))
                .andExpect(jsonPath("$[1].isRecurrenceOccurrence").value(true));
    }

    @Test
    @DisplayName("ACTIVE 멤버는 반복 일정과 회차를 생성·조회·수정·삭제한다")
    void givenActiveMember_whenManagingRecurrence_thenKeepsRecurrenceApiContract() throws Exception {
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(currentAccountId(), "group", null)
        );
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace, currentAccountId(), "nickname", START_AT
        ));
        Tag defaultTag = tagRepository.saveAndFlush(Tag.groupDefault(groupSpace));

        MvcResult created = mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/recurrence-events", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurrenceRequest("매일 회의", defaultTag.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrence[0]").value("RRULE:FREQ=DAILY"))
                .andReturn();
        Long recurrenceId = responseId(created);

        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/recurrence-events/{recurrenceId}",
                        groupSpace.getId(), recurrenceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("매일 회의"));

        mockMvc.perform(put("/api/group-spaces/{groupSpaceId}/recurrence-events/{recurrenceId}",
                        groupSpace.getId(), recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurrenceRequest("수정된 매일 회의", defaultTag.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 매일 회의"));

        mockMvc.perform(patch("/api/group-spaces/{groupSpaceId}/recurrence-events/{recurrenceId}/occurrences",
                        groupSpace.getId(), recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(occurrenceRequest("첫 회차 수정")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("첫 회차 수정"))
                .andExpect(jsonPath("$.isRecurrenceOccurrence").value(true));

        mockMvc.perform(delete("/api/group-spaces/{groupSpaceId}/recurrence-events/{recurrenceId}/occurrences",
                        groupSpace.getId(), recurrenceId)
                        .param("originStartAt", START_AT.toString()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/group-spaces/{groupSpaceId}/recurrence-events/{recurrenceId}",
                        groupSpace.getId(), recurrenceId))
                .andExpect(status().isNoContent());
    }

    private Long responseId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return body.get("id").asLong();
    }

    private String tagRequest(String title, String colorCode) {
        return "{\"title\":\"%s\",\"colorCode\":\"%s\"}".formatted(title, colorCode);
    }

    private String eventRequest(String title, Long tagId) {
        return """
                {
                  "title": "%s",
                  "startAt": "%s",
                  "endAt": "%s",
                  "allDay": false,
                  "timeZone": "UTC",
                  "tagId": %d
                }
                """.formatted(title, START_AT, END_AT, tagId);
    }

    private String recurrenceRequest(String title, Long tagId) {
        return """
                {
                  "title": "%s",
                  "allDay": false,
                  "firstOccurrenceStartAt": "%s",
                  "firstOccurrenceEndAt": "%s",
                  "timeZone": "UTC",
                  "recurrence": ["RRULE:FREQ=DAILY"],
                  "tagId": %d
                }
                """.formatted(title, START_AT, END_AT, tagId);
    }

    private String occurrenceRequest(String title) {
        return """
                {
                  "originStartAt": "%s",
                  "title": "%s",
                  "startAt": "%s",
                  "endAt": "%s",
                  "allDay": false,
                  "timeZone": "UTC"
                }
                """.formatted(START_AT, title, START_AT.plusSeconds(3600), END_AT.plusSeconds(3600));
    }
}
