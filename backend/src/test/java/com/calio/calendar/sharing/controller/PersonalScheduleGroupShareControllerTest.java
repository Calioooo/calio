package com.calio.calendar.sharing.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountAuthToken;
import com.calio.calendar.account.repository.AccountAuthTokenRepository;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.auth.service.AccessTokenEncoder;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.sharing.event.repository.PersonalEventGroupShareRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:personal-schedule-group-share-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class PersonalScheduleGroupShareControllerTest {

    private static final Instant MEMBER_CREATED_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountAuthTokenRepository accountAuthTokenRepository;
    @Autowired private AccessTokenEncoder accessTokenEncoder;
    @Autowired private EventRepository eventRepository;
    @Autowired private RecurrenceEventRepository recurrenceEventRepository;
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
        eventRepository.deleteAll();
        recurrenceEventRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupSpaceRepository.deleteAll();
        tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(
                TagType.PERSONAL_DEFAULT, "기타"
        ).orElseGet(() -> tagRepository.save(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B")));
    }

    @Test
    @DisplayName("단건 일정 다중 공유는 대상별 부분 성공과 중복 요청의 멱등 결과를 반환한다")
    void givenMixedAndDuplicatedTargets_whenCreateEventGroupShares_thenReturnsPartialIdempotentResults()
            throws Exception {
        long eventId = createEvent("공유할 일정");
        GroupSpace eligible = createGroupWithCurrentMember("eligible");
        GroupSpace anotherEligible = createGroupWithCurrentMember("another-eligible");
        GroupSpace ineligible = groupSpaceRepository.saveAndFlush(
                new GroupSpace(accountId, "ineligible", null)
        );

        mockMvc.perform(post("/api/events/group-shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventShareRequest(eventId, eligible.getId(), ineligible.getId(), eligible.getId(), anotherEligible.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].eventId").value(eventId))
                .andExpect(jsonPath("$.results[0].targets", hasSize(3)))
                .andExpect(jsonPath("$.results[0].targets[0].groupSpaceId").value(eligible.getId()))
                .andExpect(jsonPath("$.results[0].targets[0].status").value("SHARED"))
                .andExpect(jsonPath("$.results[0].targets[1].groupSpaceId").value(ineligible.getId()))
                .andExpect(jsonPath("$.results[0].targets[1].status").value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.results[0].targets[2].groupSpaceId").value(anotherEligible.getId()))
                .andExpect(jsonPath("$.results[0].targets[2].status").value("SHARED"));

        mockMvc.perform(post("/api/events/group-shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventShareRequest(eventId, eligible.getId(), anotherEligible.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results[0].targets", hasSize(2)))
                .andExpect(jsonPath("$.results[0].targets[0].status").value("ALREADY_SHARED"))
                .andExpect(jsonPath("$.results[0].targets[1].status").value("ALREADY_SHARED"));

        assertThat(eventShareRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("반복 일정 다중 공유는 전체 series의 대상별 결과와 초기 익명 여부를 반환한다")
    void givenMixedTargets_whenCreateRecurrenceGroupShares_thenReturnsWholeSeriesTargetResults()
            throws Exception {
        long recurrenceId = createRecurrence("매일 회의");
        GroupSpace eligible = createGroupWithCurrentMember("eligible");
        GroupSpace ineligible = groupSpaceRepository.saveAndFlush(
                new GroupSpace(accountId, "ineligible", null)
        );

        mockMvc.perform(post("/api/recurrence-events/{recurrenceId}/group-shares", recurrenceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupSpaceIds": [%d, %d, %d],
                                  "isAnonymous": true
                                }
                                """.formatted(eligible.getId(), ineligible.getId(), eligible.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$.targets", hasSize(2)))
                .andExpect(jsonPath("$.targets[0].status").value("SHARED"))
                .andExpect(jsonPath("$.targets[1].status").value("NOT_ELIGIBLE"));

        mockMvc.perform(get("/api/recurrence-events/{recurrenceId}/group-shares", recurrenceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].groupSpaceId").value(eligible.getId()))
                .andExpect(jsonPath("$[0].isAnonymous").value(true))
                .andExpect(jsonPath("$[0].publicShareId").isString());
    }

    @Test
    @DisplayName("원본 소유자만 공유 상태를 변경·해제할 수 있고 Group Space owner도 타인 mapping을 관리할 수 없다")
    void givenSharedEvent_whenManagingShare_thenOnlySourceOwnerCanChangeOrRemoveIt() throws Exception {
        long eventId = createEvent("소유자 일정");
        Account groupOwner = accountRepository.saveAndFlush(new Account());
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(groupOwner.getId(), "owner-group", null)
        );
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace, groupOwner.getId(), "owner", MEMBER_CREATED_AT
        ));
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace, accountId, "source", MEMBER_CREATED_AT
        ));
        createEventShare(eventId, groupSpace.getId());
        String sourceOwnerToken = createAuthenticatedToken(accountRepository.getReferenceById(accountId));

        MvcResult listed = mockMvc.perform(get("/api/events/{eventId}/group-shares", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].groupSpaceId").value(groupSpace.getId()))
                .andExpect(jsonPath("$[0].isAnonymous").value(true))
                .andReturn();
        String publicShareId = response(listed).get(0).get("publicShareId").asString();

        mockMvc.perform(patch("/api/events/{eventId}/group-shares/{groupSpaceId}", eventId, groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAnonymous\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAnonymous").value(false))
                .andExpect(jsonPath("$.publicShareId").value(publicShareId));

        mockMvc.perform(delete("/api/events/{eventId}/group-shares/{groupSpaceId}", eventId, groupSpace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createAuthenticatedToken(groupOwner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PERSONAL_SCHEDULE_SHARE_FORBIDDEN"));
        assertThat(eventShareRepository.count()).isEqualTo(1);

        mockMvc.perform(delete("/api/events/{eventId}/group-shares/{groupSpaceId}", eventId, groupSpace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sourceOwnerToken))
                .andExpect(status().isNoContent());
        assertThat(eventShareRepository.count()).isZero();
    }

    @Test
    @DisplayName("Group Space owner가 멤버를 강퇴하면 해당 멤버의 mapping만 정리하고 개인 원본 일정은 보존한다")
    void givenSharedEvent_whenGroupOwnerKicksSourceMember_thenCleansMappingAndPreservesSource()
            throws Exception {
        long eventId = createEvent("강퇴 대상 일정");
        Account groupOwner = accountRepository.saveAndFlush(new Account());
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(groupOwner.getId(), "cleanup-group", null)
        );
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace, groupOwner.getId(), "owner", MEMBER_CREATED_AT
        ));
        GroupMember sourceMember = groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace, accountId, "source", MEMBER_CREATED_AT
        ));
        createEventShare(eventId, groupSpace.getId());

        mockMvc.perform(delete("/api/group-spaces/{groupSpaceId}/members/{memberId}",
                        groupSpace.getId(), sourceMember.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createAuthenticatedToken(groupOwner)))
                .andExpect(status().isNoContent());

        assertThat(eventShareRepository.count()).isZero();
        assertThat(eventRepository.findById(eventId)).isPresent();
    }

    private GroupSpace createGroupWithCurrentMember(String name) {
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(accountId, name, null)
        );
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace, accountId, name.substring(0, Math.min(name.length(), 9)), MEMBER_CREATED_AT
        ));
        return groupSpace;
    }

    private long createEvent(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "startAt": "2026-08-01T09:00:00Z",
                                  "endAt": "2026-08-01T10:00:00Z",
                                  "allDay": false,
                                  "timeZone": "UTC"
                                }
                                """.formatted(title)))
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

    private void createEventShare(long eventId, long groupSpaceId) throws Exception {
        mockMvc.perform(post("/api/events/group-shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventShareRequest(eventId, groupSpaceId)))
                .andExpect(status().isCreated());
    }

    private String eventShareRequest(long eventId, long... groupSpaceIds) {
        String groups = java.util.Arrays.stream(groupSpaceIds)
                .mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));
        return """
                {
                  "eventIds": [%d],
                  "groupSpaceIds": [%s],
                  "isAnonymous": true
                }
                """.formatted(eventId, groups);
    }

    private String createAuthenticatedToken(Account account) {
        String rawToken = accessTokenEncoder.generateRawToken();
        accountAuthTokenRepository.saveAndFlush(
                new AccountAuthToken(account, accessTokenEncoder.hash(rawToken))
        );
        return rawToken;
    }

    private JsonNode response(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
