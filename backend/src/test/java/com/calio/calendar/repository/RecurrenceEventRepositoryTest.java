package com.calio.calendar.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.RecurrenceFrequency;
import com.calio.calendar.repository.entity.Tag;
import com.calio.calendar.repository.entity.TagType;
import java.time.LocalDate;
import java.time.LocalTime;
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
    @DisplayName("후보 rule 조회는 전날 밤에 시작해 요청일 새벽에 겹치는 overnight rule을 배제하지 않는다")
    void givenOvernightRuleStartingPreviousDay_whenFindEligibleRules_thenIncludesCandidate() {
        // given
        Account account = accountRepository.save(new Account());
        Tag tag = tagRepository.save(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        RecurrenceEvent overnightRule = recurrenceEventRepository.save(new RecurrenceEvent(
                "Overnight",
                null,
                LocalDate.parse("2027-07-01"),
                LocalDate.parse("2027-07-01"),
                LocalTime.parse("23:00:00"),
                LocalTime.parse("01:00:00"),
                RecurrenceFrequency.DAILY,
                tag,
                account
        ));

        // when
        var candidates = recurrenceEventRepository.findEligibleRules(
                account.getId(),
                LocalDate.parse("2027-07-01"),
                LocalDate.parse("2027-07-02")
        );

        // then
        assertThat(candidates)
                .extracting(RecurrenceEvent::getId)
                .contains(overnightRule.getId());
    }
}
