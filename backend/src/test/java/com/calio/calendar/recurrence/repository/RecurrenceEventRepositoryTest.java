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
import java.time.Instant;
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
    @DisplayName("종료 없는 RFC master도 first occurrence가 조회 종료보다 앞서면 후보에 포함한다")
    void givenUnboundedRule_whenFindExpansionCandidates_thenIncludesMaster() {
        // given
        Account account = accountRepository.save(new Account());
        Tag tag = tagRepository.save(Tag.personalDefault("기타", "#64748B"));
        RecurrenceEvent master = saveRecurrenceEvent(
                account,
                tag,
                "Unbounded",
                "2020-01-01T09:00:00Z"
        );
        entityManager.clear();

        // when
        List<RecurrenceEvent> candidates = recurrenceEventRepository.findExpansionCandidatesStartedBefore(
                account.getId(),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        // then
        assertThat(candidates)
                .extracting(RecurrenceEvent::getId)
                .containsExactly(master.getId());
        assertThat(candidates.getFirst().getRecurrenceRules())
                .containsExactly("RRULE:FREQ=DAILY");
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(candidates.getFirst(), "tag"))
                .isTrue();
    }

    @Test
    @DisplayName("account가 같고 first occurrence가 조회 종료보다 앞선 master만 후보로 조회한다")
    void givenMastersAcrossAccountsAndTime_whenFindExpansionCandidates_thenScopesByAccountAndStart() {
        // given
        Account account = accountRepository.save(new Account());
        Account otherAccount = accountRepository.save(new Account());
        Tag tag = tagRepository.save(Tag.personalDefault("기타", "#64748B"));
        RecurrenceEvent included = saveRecurrenceEvent(
                account,
                tag,
                "Included",
                "2026-02-01T09:00:00Z"
        );
        saveRecurrenceEvent(account, tag, "Future", "2026-03-01T09:00:00Z");
        saveRecurrenceEvent(otherAccount, tag, "Other account", "2026-02-01T09:00:00Z");
        entityManager.clear();

        // when
        List<RecurrenceEvent> candidates = recurrenceEventRepository.findExpansionCandidatesStartedBefore(
                account.getId(),
                Instant.parse("2026-02-20T00:00:00Z")
        );

        // then
        assertThat(candidates)
                .extracting(RecurrenceEvent::getId)
                .containsExactly(included.getId());
    }

    private RecurrenceEvent saveRecurrenceEvent(
            Account account,
            Tag tag,
            String title,
            String firstOccurrenceStartAt
    ) {
        Instant startAt = Instant.parse(firstOccurrenceStartAt);
        return recurrenceEventRepository.save(new RecurrenceEvent(
                title,
                null,
                RecurrenceSchedule.create(
                        false,
                        startAt,
                        startAt.plusSeconds(3_600),
                        "UTC"
                ),
                List.of("RRULE:FREQ=DAILY"),
                tag,
                account
        ));
    }
}
