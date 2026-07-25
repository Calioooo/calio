package com.calio.calendar.recurrence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import jakarta.persistence.EntityManager;
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

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("account recurrence 조회는 종료일 없는 무기한 RFC master도 후보에서 배제하지 않는다")
    void givenUnboundedRule_whenFindRecurrenceEvents_thenIncludesMaster() {
        // given
        Account account = accountRepository.save(new Account());
        Tag tag = tagRepository.save(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        RecurrenceEvent master = recurrenceEventRepository.saveAndFlush(new RecurrenceEvent(
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
        entityManager.clear();

        // when
        List<RecurrenceEvent> candidates = recurrenceEventRepository.findOverlappingRecurrenceEvents(
                account.getId(),
                LocalDate.parse("2020-01-01"),
                LocalDate.parse("2020-01-02")
        );

        // then
        assertThat(candidates)
                .extracting(RecurrenceEvent::getId)
                .contains(master.getId());
        assertThat(candidates.getFirst().getRecurrenceRules())
                .containsExactly("RRULE:FREQ=DAILY");
    }

    @Test
    @DisplayName("반복 기간이 조회 기간과 겹치는 master만 조회한다")
    void givenRecurrenceEventsAcrossDateRange_whenFindOverlapping_thenReturnsOnlyCandidates() {
        // given
        Account account = accountRepository.save(new Account());
        Tag tag = tagRepository.save(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        saveRecurrenceEvent(account, tag, "Before", "2026-01-01", "2026-01-31");
        RecurrenceEvent overlapping = saveRecurrenceEvent(account, tag, "Overlapping", "2026-02-01", "2026-02-28");
        saveRecurrenceEvent(account, tag, "After", "2026-03-01", "2026-03-31");
        entityManager.clear();

        // when
        List<RecurrenceEvent> candidates = recurrenceEventRepository.findOverlappingRecurrenceEvents(
                account.getId(),
                LocalDate.parse("2026-02-10"),
                LocalDate.parse("2026-02-20")
        );

        // then
        assertThat(candidates)
                .extracting(RecurrenceEvent::getId)
                .containsExactly(overlapping.getId());
    }

    @Test
    @DisplayName("all-day endDate가 조회일보다 앞서도 RRULE 후보에서 제외하지 않는다")
    void givenAllDaySeriesExtendingPastOccurrenceEnd_whenFindCandidates_thenIncludesMaster() {
        // given
        Account account = accountRepository.save(new Account());
        Tag tag = tagRepository.save(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        RecurrenceEvent master = recurrenceEventRepository.saveAndFlush(new RecurrenceEvent(
                "All-day series",
                null,
                RecurrenceSchedule.create(
                        true,
                        LocalDate.parse("2026-09-01"),
                        LocalDate.parse("2026-09-02"),
                        null,
                        null,
                        null
                ),
                List.of("RRULE:FREQ=DAILY;COUNT=10"),
                tag,
                account
        ));
        entityManager.clear();

        // when
        List<RecurrenceEvent> candidates = recurrenceEventRepository.findOverlappingRecurrenceEvents(
                account.getId(),
                LocalDate.parse("2026-09-05"),
                LocalDate.parse("2026-09-05")
        );

        // then
        assertThat(candidates)
                .extracting(RecurrenceEvent::getId)
                .containsExactly(master.getId());
    }

    private RecurrenceEvent saveRecurrenceEvent(
            Account account,
            Tag tag,
            String title,
            String startDate,
            String endDate
    ) {
        return recurrenceEventRepository.save(new RecurrenceEvent(
                title,
                null,
                RecurrenceSchedule.create(
                        false,
                        LocalDate.parse(startDate),
                        LocalDate.parse(endDate),
                        LocalTime.parse("09:00"),
                        LocalTime.parse("10:00"),
                        "UTC"
                ),
                List.of("RRULE:FREQ=DAILY"),
                tag,
                account
        ));
    }
}
