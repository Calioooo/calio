package com.calio.calendar.recurrence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-recurrence-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RecurrenceEventRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RecurrenceEventRepository recurrenceEventRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @DisplayName("account recurrence 조회는 종료일 없는 무기한 RFC master도 후보에서 배제하지 않는다")
    void givenUnboundedRule_whenFindRecurrenceEvents_thenIncludesMaster() {
        // given
        Account account = accountRepository.save(new Account());
        Tag tag = tagRepository.save(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        RecurrenceEvent master = recurrenceEventRepository.save(new RecurrenceEvent(
                "Unbounded",
                null,
                RecurrenceSchedule.create(
                        false,
                        LocalDate.parse("2020-01-01"),
                        LocalDate.parse("2020-01-01"),
                        LocalTime.parse("09:00"),
                        LocalTime.parse("10:00"),
                        "UTC"
                ),
                List.of("RRULE:FREQ=DAILY"),
                tag,
                account
        ));

        // when
        List<RecurrenceEvent> candidates = recurrenceEventRepository.findRecurrenceEvents(account.getId());

        // then
        assertThat(candidates)
                .extracting(RecurrenceEvent::getId)
                .contains(master.getId());
    }
}
