package com.calio.calendar.recurrence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
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
        "spring.datasource.url=jdbc:h2:mem:calendar-recurrence-override-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RecurrenceEventOverrideRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private RecurrenceEventRepository recurrenceEventRepository;

    @Autowired
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("활성 기간과 겹치는 override만 조회하고 master와 tag를 함께 로딩한다")
    void givenActiveAndDeletedOverrides_whenFindActiveOverlapping_thenReturnsLoadedActiveOverride() {
        // given
        Account account = accountRepository.save(new Account());
        Tag tag = tagRepository.save(Tag.personalDefault("기타", "#64748B"));
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.save(new RecurrenceEvent(
                "Rule",
                null,
                RecurrenceSchedule.create(
                        false,
                        Instant.parse("2027-01-01T09:00:00Z"),
                        Instant.parse("2027-01-01T10:00:00Z"),
                        "UTC"
                ),
                List.of("RRULE:FREQ=DAILY"),
                tag,
                account
        ));
        RecurrenceEventOverride activeOverride = recurrenceEventOverrideRepository.save(
                RecurrenceEventOverride.active(
                        recurrenceEvent,
                        Instant.parse("2027-01-02T09:00:00Z"),
                        "Moved",
                        null,
                        CanonicalSchedule.recurrenceOverride(
                                Instant.parse("2027-01-02T10:00:00Z"),
                                Instant.parse("2027-01-02T11:00:00Z"),
                                false,
                                "UTC"
                        )
                )
        );
        recurrenceEventOverrideRepository.save(RecurrenceEventOverride.deleted(
                recurrenceEvent,
                Instant.parse("2027-01-03T09:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z")
        ));
        recurrenceEventOverrideRepository.flush();
        entityManager.clear();

        // when
        List<RecurrenceEventOverride> overrides =
                recurrenceEventOverrideRepository.findActiveOverlappingOverrides(
                        account.getId(),
                        Instant.parse("2027-01-02T09:30:00Z"),
                        Instant.parse("2027-01-02T11:30:00Z")
                );

        // then
        assertThat(overrides)
                .extracting(RecurrenceEventOverride::getOverrideId)
                .containsExactly(activeOverride.getOverrideId());
        RecurrenceEventOverride loadedOverride = overrides.getFirst();
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(loadedOverride, "recurrenceEvent"))
                .isTrue();
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(loadedOverride.getRecurrenceEvent(), "tag"))
                .isTrue();
    }
}
