package com.calio.calendar.groupcalendar.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
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
        "spring.datasource.url=jdbc:h2:mem:shared-personal-group-calendar-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class SharedPersonalScheduleGroupCalendarControllerTest {

    private static final Instant MEMBER_CREATED_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EventRepository eventRepository;
    @Autowired private RecurrenceEventRepository recurrenceEventRepository;
    @Autowired private RecurrenceEventOverrideRepository recurrenceOverrideRepository;
    @Autowired private PersonalEventGroupShareRepository eventShareRepository;
    @Autowired private PersonalRecurrenceGroupShareRepository recurrenceShareRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupSpaceRepository groupSpaceRepository;
    @Autowired private TagRepository tagRepository;

    private Long accountId;

    @BeforeEach
    void setUp() {
        accountId = currentAccountId();
        eventShareRepository.deleteAll();
        recurrenceShareRepository.deleteAll();
        recurrenceOverrideRepository.deleteAll();
        eventRepository.deleteAll();
        recurrenceEventRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupSpaceRepository.deleteAll();
        tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(
                TagType.PERSONAL_DEFAULT, "기타"
        ).orElseGet(() -> tagRepository.save(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B")));
    }

    @Test
    @DisplayName("공유 단건 일정은 opaque public ID만 노출하고 익명 대상에서는 원본 상세를 숨긴다")
    void givenVisibleAndAnonymousShares_whenListGroupCalendar_thenExcludesPrivateSourceFields()
            throws Exception {
        GroupSpace groupSpace = createGroupSpace();
        Event visible = eventRepository.getReferenceById(createEvent("공개 제목", "2026-08-01T09:00:00Z"));
        Event anonymous = eventRepository.getReferenceById(createEvent("비공개 제목", "2026-08-01T11:00:00Z"));
        PersonalEventGroupShare visibleShare = eventShareRepository.saveAndFlush(
                PersonalEventGroupShare.create(visible, groupSpace, false)
        );
        PersonalEventGroupShare anonymousShare = eventShareRepository.saveAndFlush(
                PersonalEventGroupShare.create(anonymous, groupSpace, true)
        );

        mockMvc.perform(groupCalendarRequest(groupSpace, "2026-08-01T00:00:00Z", "2026-08-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("공개 제목"))
                .andExpect(jsonPath("$[0].publicItemId").value(
                        "shared-event:" + visibleShare.getPublicShareId()
                ))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].recurrenceId").doesNotExist())
                .andExpect(jsonPath("$[0].tag").doesNotExist())
                .andExpect(jsonPath("$[0].creatorNickname").doesNotExist())
                .andExpect(jsonPath("$[1].title").value("익명 일정"))
                .andExpect(jsonPath("$[1].description").isEmpty())
                .andExpect(jsonPath("$[1].publicItemId").value(
                        "shared-event:" + anonymousShare.getPublicShareId()
                ))
                .andExpect(jsonPath("$[1].publicItemId").value(startsWith("shared-event:")))
                .andExpect(jsonPath("$[1].id").doesNotExist())
                .andExpect(jsonPath("$[1].recurrenceId").doesNotExist())
                .andExpect(jsonPath("$[1].tag").doesNotExist())
                .andExpect(jsonPath("$[1].creatorNickname").doesNotExist());
    }

    @Test
    @DisplayName("공유 반복 일정은 원본 override를 반영하고 취소 회차와 private source ID를 노출하지 않는다")
    void givenSourceOverrides_whenListSharedRecurrence_thenProjectsOverrideAndCancellation()
            throws Exception {
        GroupSpace groupSpace = createGroupSpace();
        long recurrenceId = createRecurrence("원본 반복 일정");
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.getReferenceById(recurrenceId);
        PersonalRecurrenceGroupShare share = recurrenceShareRepository.saveAndFlush(
                PersonalRecurrenceGroupShare.create(recurrenceEvent, groupSpace, false)
        );

        mockMvc.perform(patch("/api/recurrence-events/{recurrenceId}/occurrences", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originStartAt": "2026-08-01T09:00:00Z",
                                  "title": "이동한 원본 override",
                                  "description": "override 설명",
                                  "startAt": "2026-08-05T12:00:00Z",
                                  "endAt": "2026-08-05T13:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/recurrence-events/{recurrenceId}/occurrences", recurrenceId)
                        .param("originStartAt", "2026-08-02T09:00:00Z"))
                .andExpect(status().isNoContent());

        mockMvc.perform(groupCalendarRequest(groupSpace, "2026-08-01T00:00:00Z", "2026-08-06T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("원본 반복 일정"))
                .andExpect(jsonPath("$[0].startAt").value("2026-08-03T09:00:00Z"))
                .andExpect(jsonPath("$[0].publicItemId").value(
                        "shared-recurrence:" + share.getPublicShareId() + ":2026-08-03T09:00:00Z"
                ))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].recurrenceId").doesNotExist())
                .andExpect(jsonPath("$[0].tag").doesNotExist())
                .andExpect(jsonPath("$[1].title").value("이동한 원본 override"))
                .andExpect(jsonPath("$[1].description").value("override 설명"))
                .andExpect(jsonPath("$[1].startAt").value("2026-08-05T12:00:00Z"))
                .andExpect(jsonPath("$[1].publicItemId").value(
                        "shared-recurrence:" + share.getPublicShareId() + ":2026-08-01T09:00:00Z"
                ))
                .andExpect(jsonPath("$[1].id").doesNotExist())
                .andExpect(jsonPath("$[1].recurrenceId").doesNotExist())
                .andExpect(jsonPath("$[1].tag").doesNotExist())
                .andExpect(jsonPath("$[1].creatorNickname").doesNotExist());
    }

    private GroupSpace createGroupSpace() {
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(accountId, "공유 그룹", null)
        );
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace, accountId, "공유자", MEMBER_CREATED_AT
        ));
        return groupSpace;
    }

    private long createEvent(String title, String startAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "개인 원본 설명",
                                  "startAt": "%s",
                                  "endAt": "%s",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """.formatted(title, startAt, Instant.parse(startAt).plusSeconds(3600))))
                .andExpect(status().isCreated())
                .andReturn();
        return response(result).get("id").asLong();
    }

    private long createRecurrence(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/recurrence-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "개인 반복 원본 설명",
                                  "allDay": false,
                                  "firstOccurrenceStartAt": "2026-08-01T09:00:00Z",
                                  "firstOccurrenceEndAt": "2026-08-01T10:00:00Z",
                                  "timeZone": "UTC",
                                  "recurrence": ["RRULE:FREQ=DAILY;COUNT=3"]
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return response(result).get("recurrenceId").asLong();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder groupCalendarRequest(
            GroupSpace groupSpace,
            String from,
            String to
    ) {
        return get("/api/group-spaces/{groupSpaceId}/calendar/shared-items", groupSpace.getId())
                .param("from", from)
                .param("to", to);
    }

    private JsonNode response(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
