package com.calio.calendar.groupcalendar.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
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
import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceOverrideRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.sharing.event.repository.PersonalEventGroupShareRepository;
import com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
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
    @Autowired private AccountRepository accountRepository;
    @Autowired private EventRepository personalEventRepository;
    @Autowired private RecurrenceEventRepository personalRecurrenceEventRepository;
    @Autowired private PersonalEventGroupShareRepository personalEventGroupShareRepository;
    @Autowired private PersonalRecurrenceGroupShareRepository personalRecurrenceGroupShareRepository;

    @BeforeEach
    void setUp() {
        personalEventGroupShareRepository.deleteAll();
        personalRecurrenceGroupShareRepository.deleteAll();
        personalEventRepository.deleteAll();
        personalRecurrenceEventRepository.deleteAll();
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
        Tag defaultTag = tagRepository.saveAndFlush(new Tag(TagType.GROUP_DEFAULT, "기타", "#64748B", groupSpace));
        GroupSpace anotherGroupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(currentAccountId(), "another-group", null)
        );
        groupMemberRepository.saveAndFlush(new GroupMember(
                anotherGroupSpace,
                currentAccountId(),
                "other",
                START_AT
        ));
        Tag anotherGroupTag = tagRepository.saveAndFlush(Tag.groupDefault(anotherGroupSpace));

        // when
        MvcResult createdTag = mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/tags", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagRequest("업무", "#2563EB")))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tagType").value("CUSTOM"))
                .andReturn();
        Long tagId = responseId(createdTag);

        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/tags", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagRequest("업무", "#2563EB")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/events", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest("다른 그룹 태그", anotherGroupTag.getId())))
                .andExpect(status().isNotFound());

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

        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/calendar/group-items", groupSpace.getId())
                        .param("from", START_AT.minusSeconds(1).toString())
                        .param("to", END_AT.plusSeconds(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].creatorNickname").value("nickname"));

        mockMvc.perform(delete("/api/group-spaces/{groupSpaceId}/tags/{tagId}", groupSpace.getId(), tagId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/events", groupSpace.getId())
                        .param("from", START_AT.minusSeconds(1).toString())
                        .param("to", END_AT.plusSeconds(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tag.id").value(defaultTag.getId()));
        mockMvc.perform(delete("/api/group-spaces/{groupSpaceId}/events/{eventId}", groupSpace.getId(), eventId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Group Calendar API는 비멤버와 필수값이 누락된 요청을 거절한다")
    void givenNonMemberOrInvalidRequest_whenCallingCalendarApi_thenReturnsContractError() throws Exception {
        // given
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(new GroupSpace(999L, "other", null));

        // when, then
        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/calendar/group-items", groupSpace.getId())
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

        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/calendar/group-items", groupSpace.getId())
                        .param("from", START_AT.minusSeconds(1).toString())
                        .param("to", END_AT.plusSeconds(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].creatorNickname").value("nickname"))
                .andExpect(jsonPath("$[1].creatorNickname").value("nickname"))
                .andExpect(jsonPath("$[0].publicItemId").value(startsWith("group-event:")))
                .andExpect(jsonPath("$[1].publicItemId").value(startsWith("group-recurrence:")));
    }

    @Test
    @DisplayName("익명 공유 일정은 공개 식별자만 노출하고 원본 식별자와 태그를 노출하지 않는다")
    void givenAnonymousSharedSchedules_whenListCalendarItems_thenKeepsPublicItemJsonContract() throws Exception {
        // given
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(currentAccountId(), "group", null)
        );
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace, currentAccountId(), "nickname", START_AT
        ));
        Account account = accountRepository.findById(currentAccountId()).orElseThrow();
        Tag personalTag = tagRepository.saveAndFlush(Tag.personalCustom(account, "개인", "#2563EB"));
        Event sharedEvent = personalEventRepository.saveAndFlush(new Event(
                "비공개 단건 일정",
                "비공개 단건 설명",
                START_AT.plusSeconds(7200),
                END_AT.plusSeconds(7200),
                false,
                "UTC",
                null,
                personalTag,
                account
        ));
        PersonalEventGroupShare eventShare = personalEventGroupShareRepository.saveAndFlush(
                PersonalEventGroupShare.create(sharedEvent, groupSpace, true)
        );
        RecurrenceEvent sharedRecurrenceEvent = personalRecurrenceEventRepository.saveAndFlush(
                new RecurrenceEvent(
                        "비공개 반복 일정",
                        "비공개 반복 설명",
                        RecurrenceSchedule.create(false, START_AT, END_AT, "UTC"),
                        List.of("RRULE:FREQ=DAILY;COUNT=1"),
                        personalTag,
                        account
                )
        );
        PersonalRecurrenceGroupShare recurrenceShare = personalRecurrenceGroupShareRepository.saveAndFlush(
                PersonalRecurrenceGroupShare.create(sharedRecurrenceEvent, groupSpace, true)
        );

        // when
        MvcResult result = mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/calendar/shared-items", groupSpace.getId())
                        .param("from", START_AT.minusSeconds(1).toString())
                        .param("to", END_AT.plusSeconds(10801).toString()))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        JsonNode eventItem = itemByPublicItemId(items, "shared-event:" + eventShare.getPublicShareId());
        JsonNode recurrenceItem = itemByPublicItemId(
                items,
                "shared-recurrence:" + recurrenceShare.getPublicShareId() + ":" + START_AT
        );

        assertThat(eventItem).isNotNull();
        assertThat(recurrenceItem).isNotNull();
        assertThat(eventItem.get("title").asString()).isEqualTo("익명 일정");
        assertThat(recurrenceItem.get("title").asString()).isEqualTo("익명 일정");
        assertThat(eventItem.has("accountId")).isFalse();
        assertThat(recurrenceItem.has("accountId")).isFalse();
        assertThat(eventItem.has("id")).isFalse();
        assertThat(eventItem.has("recurrenceId")).isFalse();
        assertThat(eventItem.has("tag")).isFalse();
        assertThat(recurrenceItem.has("id")).isFalse();
        assertThat(recurrenceItem.has("recurrenceId")).isFalse();
        assertThat(recurrenceItem.has("tag")).isFalse();
        assertThat(eventItem.has("creatorNickname")).isFalse();
        assertThat(recurrenceItem.has("creatorNickname")).isFalse();
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

    private JsonNode itemByPublicItemId(JsonNode items, String publicItemId) {
        for (JsonNode item : items) {
            if (publicItemId.equals(item.get("publicItemId").asString())) {
                return item;
            }
        }
        return null;
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
