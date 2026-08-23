package com.calio.calendar.groupcalendar.sharing.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:personal-event-group-share-command-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PersonalEventGroupShareCommandServiceTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PersonalEventGroupShareCommandService commandService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @DisplayName("같은 개인 일정과 Group Space의 공유 생성 충돌은 안정적인 오류 코드로 변환한다")
    void givenDuplicateShare_whenCreateShare_thenThrowsShareConflictErrorCode() {
        // given
        Fixture fixture = fixture();
        commandService.createShare(new PersonalEventGroupShare(fixture.event(), fixture.groupSpace()));

        // when, then
        assertThatThrownBy(() -> commandService.createShare(
                new PersonalEventGroupShare(fixture.event(), fixture.groupSpace())
        )).isInstanceOfSatisfying(
                CalioException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.PERSONAL_EVENT_GROUP_SHARE_CONFLICT)
        );
    }

    private Fixture fixture() {
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B"));
        Event event = eventRepository.saveAndFlush(new Event(
                "개인 일정",
                null,
                Instant.parse("2028-01-01T09:00:00Z"),
                Instant.parse("2028-01-01T10:00:00Z"),
                false,
                "UTC",
                null,
                tag,
                account
        ));
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(account.getId(), "그룹", null)
        );
        return new Fixture(event, groupSpace);
    }

    private record Fixture(Event event, GroupSpace groupSpace) {
    }
}
