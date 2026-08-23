package com.calio.calendar.groupcalendar.sharing.recurrence.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareScope;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareSelectedOriginRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:personal-recurrence-group-share-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class PersonalRecurrenceGroupShareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private PersonalRecurrenceGroupShareRepository shareRepository;

    @Autowired
    private PersonalRecurrenceGroupShareSelectedOriginRepository selectedOriginRepository;

    @Autowired
    private RecurrenceEventRepository recurrenceEventRepository;

    @Autowired
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @DisplayName("ACTIVE 멤버는 본인 반복 일정을 전체 시리즈로 한 Group Space에 공유한다")
    void givenActiveMemberAndOwnedRecurrence_whenShareWholeSeries_thenCreatesMapping() throws Exception {
        // given
        GroupSpace groupSpace = activeGroupSpace();
        RecurrenceEvent recurrenceEvent = recurrenceEvent(currentAccountId(), "반복 일정");

        // when
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/recurrence-shares", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"recurrenceId\": %d, \"selectionEnabled\": false }"
                                .formatted(recurrenceEvent.getId())))
                // then
                .andExpect(status().isCreated());

        PersonalRecurrenceGroupShare share = shareRepository
                .findByRecurrenceEventIdAndGroupSpaceId(recurrenceEvent.getId(), groupSpace.getId())
                .orElseThrow();
        assertThat(share.getShareScope()).isEqualTo(PersonalRecurrenceGroupShareScope.WHOLE_SERIES);
    }

    @Test
    @DisplayName("다른 사용자의 반복 일정과 중복 전체 공유는 안정적인 오류 계약으로 거절한다")
    void givenUnownedOrDuplicatedRecurrence_whenShareWholeSeries_thenRejectsRequest() throws Exception {
        // given
        GroupSpace groupSpace = activeGroupSpace();
        RecurrenceEvent ownedRecurrence = recurrenceEvent(currentAccountId(), "내 반복 일정");
        RecurrenceEvent otherRecurrence = recurrenceEvent(
                accountRepository.saveAndFlush(new Account()).getId(),
                "다른 사람 반복 일정"
        );

        // when, then
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/recurrence-shares", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"recurrenceId\": %d, \"selectionEnabled\": false }"
                                .formatted(otherRecurrence.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECURRENCE_EVENT_NOT_FOUND"));

        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/recurrence-shares", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"recurrenceId\": %d, \"selectionEnabled\": false }"
                                .formatted(ownedRecurrence.getId())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/recurrence-shares", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"recurrenceId\": %d, \"selectionEnabled\": false }"
                                .formatted(ownedRecurrence.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PERSONAL_RECURRENCE_GROUP_SHARE_CONFLICT"));
    }

    @Test
    @DisplayName("선택한 유효 반복 회차는 originStartAt mapping으로만 공유한다")
    void givenValidSelectedOrigins_whenShare_thenPersistsOnlyOriginMappings() throws Exception {
        // given
        GroupSpace groupSpace = activeGroupSpace();
        RecurrenceEvent recurrenceEvent = recurrenceEvent(currentAccountId(), "선택 반복 일정");
        Instant firstOriginStartAt = Instant.parse("2028-01-01T09:00:00Z");
        Instant secondOriginStartAt = Instant.parse("2028-01-08T09:00:00Z");

        // when
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/recurrence-shares", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recurrenceId": %d,
                                  "selectionEnabled": true,
                                  "originStartAts": ["%s", "%s", "%s"]
                                }
                                """.formatted(
                                recurrenceEvent.getId(),
                                firstOriginStartAt,
                                secondOriginStartAt,
                                secondOriginStartAt
                        )))
                // then
                .andExpect(status().isCreated());

        PersonalRecurrenceGroupShare share = shareRepository
                .findByRecurrenceEventIdAndGroupSpaceId(recurrenceEvent.getId(), groupSpace.getId())
                .orElseThrow();
        assertThat(share.getShareScope()).isEqualTo(PersonalRecurrenceGroupShareScope.SELECTED_OCCURRENCES);
        assertThat(selectedOriginRepository.findAllByShareId(share.getId()))
                .extracting(selectedOrigin -> selectedOrigin.getOriginStartAt())
                .containsExactly(firstOriginStartAt, secondOriginStartAt);
        assertThat(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                recurrenceEvent.getId(),
                firstOriginStartAt
        )).isEmpty();
    }

    @Test
    @DisplayName("반복 규칙이 만들지 않는 originStartAt은 mapping을 저장하지 않고 거절한다")
    void givenInvalidSelectedOrigin_whenShare_thenRejectsWithoutPersistingShare() throws Exception {
        // given
        GroupSpace groupSpace = activeGroupSpace();
        RecurrenceEvent recurrenceEvent = recurrenceEvent(currentAccountId(), "유효성 확인 반복 일정");

        // when, then
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/recurrence-shares", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recurrenceId": %d,
                                  "selectionEnabled": true,
                                  "originStartAts": ["2028-01-02T09:00:00Z"]
                                }
                                """.formatted(recurrenceEvent.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECURRENCE_OCCURRENCE_NOT_FOUND"));

        assertThat(shareRepository.findByRecurrenceEventIdAndGroupSpaceId(
                recurrenceEvent.getId(),
                groupSpace.getId()
        )).isEmpty();
    }

    private GroupSpace activeGroupSpace() {
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(currentAccountId(), "공유 그룹", null)
        );
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace,
                currentAccountId(),
                "공유자",
                Instant.now()
        ));
        return groupSpace;
    }

    private RecurrenceEvent recurrenceEvent(Long accountId, String title) {
        Account account = accountRepository.getReferenceById(accountId);
        Tag tag = tagRepository.saveAndFlush(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B"));
        return recurrenceEventRepository.saveAndFlush(new RecurrenceEvent(
                title,
                null,
                new RecurrenceSchedule(
                        Instant.parse("2028-01-01T09:00:00Z"),
                        Instant.parse("2028-01-01T10:00:00Z"),
                        false,
                        "UTC"
                ),
                List.of("RRULE:FREQ=WEEKLY"),
                tag,
                account
        ));
    }
}
