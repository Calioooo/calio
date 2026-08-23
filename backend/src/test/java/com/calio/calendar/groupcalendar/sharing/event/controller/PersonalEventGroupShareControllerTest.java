package com.calio.calendar.groupcalendar.sharing.event.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupcalendar.sharing.event.repository.PersonalEventGroupShareRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:personal-event-group-share-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class PersonalEventGroupShareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private PersonalEventGroupShareRepository shareRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @DisplayName("ACTIVE 멤버는 선택한 본인 단건 일정을 한 Group Space에 공유한다")
    void givenActiveMemberAndOwnedEvents_whenShareSelected_thenCreatesMappings() throws Exception {
        // given
        GroupSpace groupSpace = activeGroupSpace();
        Event firstEvent = event(currentAccountId(), "첫 번째 일정");
        Event secondEvent = event(currentAccountId(), "두 번째 일정");

        // when
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/event-shares", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "selectionEnabled": true, "eventIds": [%d, %d] }
                                """.formatted(firstEvent.getId(), secondEvent.getId())))
                // then
                .andExpect(status().isCreated());

        assertThat(shareRepository.findAllByEventId(firstEvent.getId())).hasSize(1);
        assertThat(shareRepository.findAllByEventId(secondEvent.getId())).hasSize(1);
    }

    @Test
    @DisplayName("다른 사용자의 일정이나 중복 공유는 안정적인 오류 계약으로 거절한다")
    void givenUnownedOrDuplicatedEvent_whenShareSelected_thenRejectsRequest() throws Exception {
        // given
        GroupSpace groupSpace = activeGroupSpace();
        Event ownedEvent = event(currentAccountId(), "내 일정");
        Event otherEvent = event(accountRepository.saveAndFlush(new Account()).getId(), "다른 사람 일정");

        // when, then
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/event-shares", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"selectionEnabled\": true, \"eventIds\": [%d] }".formatted(otherEvent.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EVENT_NOT_FOUND"));

        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/event-shares", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"selectionEnabled\": true, \"eventIds\": [%d] }".formatted(ownedEvent.getId())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/event-shares", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"selectionEnabled\": true, \"eventIds\": [%d] }".formatted(ownedEvent.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PERSONAL_EVENT_GROUP_SHARE_CONFLICT"));
    }

    @Test
    @DisplayName("선택 공유를 끄면 요청 시점의 모든 개인 단건 일정만 공유한다")
    void givenBulkSelectionDisabled_whenShare_thenSharesCurrentOneOffEventsOnly() throws Exception {
        // given
        Long accountId = currentAccountId();
        GroupSpace groupSpace = activeGroupSpace();
        Event existingEvent = event(accountId, "기존 일정");

        // when
        mockMvc.perform(post("/api/group-spaces/{groupSpaceId}/event-shares", groupSpace.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"selectionEnabled\": false }"))
                // then
                .andExpect(status().isCreated());

        Event laterEvent = event(accountId, "나중 일정");
        assertThat(shareRepository.findAllByEventId(existingEvent.getId())).hasSize(1);
        assertThat(shareRepository.findAllByEventId(laterEvent.getId())).isEmpty();
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

    private Event event(Long accountId, String title) {
        Account account = accountRepository.getReferenceById(accountId);
        Tag tag = tagRepository.saveAndFlush(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B"));
        return eventRepository.saveAndFlush(new Event(
                title,
                null,
                Instant.parse("2028-01-01T09:00:00Z"),
                Instant.parse("2028-01-01T10:00:00Z"),
                false,
                "UTC",
                null,
                tag,
                account
        ));
    }
}
