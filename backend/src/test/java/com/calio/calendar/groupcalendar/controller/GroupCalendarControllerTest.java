package com.calio.calendar.groupcalendar.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.groupcalendar.sharing.event.repository.PersonalEventGroupShareRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareScope;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
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
    @Autowired private AccountRepository accountRepository;
    @Autowired private EventRepository personalEventRepository;
    @Autowired private PersonalEventGroupShareRepository personalEventShareRepository;
    @Autowired private PersonalRecurrenceGroupShareRepository personalRecurrenceShareRepository;
    @Autowired private RecurrenceEventRepository recurrenceEventRepository;
    @Autowired private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupSpaceRepository groupSpaceRepository;

    @BeforeEach
    void setUp() {
        personalEventShareRepository.deleteAll();
        personalRecurrenceShareRepository.deleteAll();
        personalEventRepository.deleteAll();
        recurrenceEventOverrideRepository.deleteAll();
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
    }

    @Test
    @DisplayName("Group Calendar API는 직접·공유 단건·공유 반복 일정을 origin별로 병합해 시작 시각순으로 반환한다")
    void givenDirectAndSharedSchedules_whenListItems_thenMergesSortedPublicViews() throws Exception {
        // given
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(currentAccountId(), "shared group", null)
        );
        Account sharingAccount = accountRepository.saveAndFlush(new Account());
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace,
                currentAccountId(),
                "owner",
                START_AT
        ));
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace,
                sharingAccount.getId(),
                "공유자",
                START_AT
        ));
        Tag groupTag = tagRepository.saveAndFlush(new Tag(TagType.GROUP_DEFAULT, "기타", "#64748B", groupSpace));
        Tag personalTag = tagRepository.saveAndFlush(new Tag(TagType.PERSONAL_DEFAULT, "개인 기타", "#64748B"));
        Event sourceEvent = personalEventRepository.saveAndFlush(new Event(
                "개인 원본 일정",
                "개인 설명",
                START_AT,
                END_AT,
                false,
                "UTC",
                null,
                personalTag,
                sharingAccount
        ));
        RecurrenceEvent sourceRecurrence = recurrenceEventRepository.saveAndFlush(new RecurrenceEvent(
                "개인 반복 원본",
                "반복 설명",
                new RecurrenceSchedule(
                        START_AT.plusSeconds(7200),
                        END_AT.plusSeconds(7200),
                        false,
                        "UTC"
                ),
                java.util.List.of("RRULE:FREQ=WEEKLY"),
                personalTag,
                sharingAccount
        ));
        personalEventShareRepository.saveAndFlush(new PersonalEventGroupShare(sourceEvent, groupSpace));
        personalRecurrenceShareRepository.saveAndFlush(new PersonalRecurrenceGroupShare(
                sourceRecurrence,
                groupSpace,
                PersonalRecurrenceGroupShareScope.WHOLE_SERIES
        ));
        eventRepository.saveAndFlush(new GroupCalendarEvent(
                groupSpace,
                sharingAccount,
                groupTag,
                "직접 그룹 일정",
                null,
                START_AT.plusSeconds(14400),
                END_AT.plusSeconds(14400),
                false,
                "UTC"
        ));

        // when, then
        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/calendar/items", groupSpace.getId())
                        .param("from", START_AT.minusSeconds(1).toString())
                        .param("to", END_AT.plusSeconds(21600).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].title").value("공유자의 일정"))
                .andExpect(jsonPath("$[0].isSharedPersonalSchedule").value(true))
                .andExpect(jsonPath("$[0].tag").value(nullValue()))
                .andExpect(jsonPath("$[0].shareMappingId").doesNotExist())
                .andExpect(jsonPath("$[1].title").value("공유자의 일정"))
                .andExpect(jsonPath("$[1].isSharedPersonalSchedule").value(true))
                .andExpect(jsonPath("$[2].title").value("직접 그룹 일정"))
                .andExpect(jsonPath("$[2].isSharedPersonalSchedule").value(false));
    }

    @Test
    @DisplayName("원본 반복 회차가 취소되면 공유 mapping이 남아 있어도 Group Calendar에 표시하지 않는다")
    void givenDeletedSourceRecurrenceOccurrence_whenListItems_thenExcludesSharedOccurrence() throws Exception {
        // given
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(currentAccountId(), "cancelled source group", null)
        );
        Account sharingAccount = accountRepository.saveAndFlush(new Account());
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace,
                currentAccountId(),
                "owner",
                START_AT
        ));
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace,
                sharingAccount.getId(),
                "공유자",
                START_AT
        ));
        Tag personalTag = tagRepository.saveAndFlush(new Tag(TagType.PERSONAL_DEFAULT, "개인 기타", "#64748B"));
        RecurrenceEvent sourceRecurrence = recurrenceEventRepository.saveAndFlush(new RecurrenceEvent(
                "취소된 원본 반복",
                null,
                new RecurrenceSchedule(START_AT, END_AT, false, "UTC"),
                java.util.List.of("RRULE:FREQ=WEEKLY"),
                personalTag,
                sharingAccount
        ));
        personalRecurrenceShareRepository.saveAndFlush(new PersonalRecurrenceGroupShare(
                sourceRecurrence,
                groupSpace,
                PersonalRecurrenceGroupShareScope.WHOLE_SERIES
        ));
        recurrenceEventOverrideRepository.saveAndFlush(RecurrenceEventOverride.deleted(
                sourceRecurrence,
                START_AT,
                START_AT
        ));

        // when, then
        mockMvc.perform(get("/api/group-spaces/{groupSpaceId}/calendar/items", groupSpace.getId())
                        .param("from", START_AT.minusSeconds(1).toString())
                        .param("to", END_AT.plusSeconds(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
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
}
