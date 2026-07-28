package com.calio.calendar.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-google-recurrence-mapping-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GoogleCalendarRecurrenceMappingRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private RecurrenceEventRepository recurrenceEventRepository;

    @Autowired
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @Autowired
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private GoogleCalendarRecurrenceEventMappingRepository eventMappingRepository;

    @Autowired
    private GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository;

    @Test
    @Transactional
    @DisplayName("recurrence provider mapping은 canonical/provider identity와 parent join으로 조회한다")
    void givenRecurrenceMappings_whenLookup_thenKeepsCanonicalAndProviderIdentitySeparate() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.saveAndFlush(
                recurrenceEvent(account, tag)
        );
        RecurrenceEventOverride recurrenceOverride = recurrenceEventOverrideRepository.saveAndFlush(
                RecurrenceEventOverride.active(
                        recurrenceEvent,
                        Instant.parse("2026-07-21T00:00:00Z"),
                        "Moved",
                        null,
                        Instant.parse("2026-07-21T01:00:00Z"),
                        Instant.parse("2026-07-21T02:00:00Z")
                )
        );
        String externalRecurrenceEventId = "m".repeat(1024);
        String externalRecurrenceOverrideId = "e".repeat(1024);
        String etag = "t".repeat(1024);
        GoogleCalendarRecurrenceEventMapping eventMapping = eventMappingRepository.saveAndFlush(
                new GoogleCalendarRecurrenceEventMapping(
                        integration,
                        recurrenceEvent,
                        externalRecurrenceEventId,
                        etag,
                        null
                )
        );
        GoogleCalendarRecurrenceOverrideMapping overrideMapping =
                overrideMappingRepository.saveAndFlush(
                        new GoogleCalendarRecurrenceOverrideMapping(
                                eventMapping,
                                recurrenceOverride,
                                externalRecurrenceOverrideId,
                                etag,
                                null
                        )
                );

        // when, then
        assertThat(eventMappingRepository.findByRecurrenceEvent_Id(recurrenceEvent.getId()))
                .map(GoogleCalendarRecurrenceEventMapping::getExternalEventId)
                .contains(externalRecurrenceEventId);
        assertThat(eventMappingRepository
                .findByIntegration_IdAndCalendarKeyAndExternalEventId(
                        integration.getId(),
                        GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                        externalRecurrenceEventId
                ))
                .map(GoogleCalendarRecurrenceEventMapping::getProviderEtag)
                .contains(etag);
        assertThat(overrideMappingRepository.findAllByExternalIdentity(
                integration.getId(),
                GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                externalRecurrenceOverrideId
        )).extracting(GoogleCalendarRecurrenceOverrideMapping::getId)
                .containsExactly(overrideMapping.getId());
        assertThat(overrideMapping.getProviderEtag()).isEqualTo(etag);

        overrideMappingRepository.delete(overrideMapping);
        overrideMappingRepository.flush();
        eventMappingRepository.delete(eventMapping);
        eventMappingRepository.flush();
        assertThat(recurrenceEventRepository.findById(recurrenceEvent.getId())).isPresent();
        assertThat(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                recurrenceEvent.getId(),
                recurrenceOverride.getOriginStartAt()
        )).isPresent();
    }

    private GoogleCalendarIntegration integration(Long accountId) {
        return new GoogleCalendarIntegration(
                accountId,
                "subject",
                "user@example.com",
                "encrypted-refresh",
                "encrypted-access",
                Instant.parse("2026-07-01T01:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z")
        );
    }

    private RecurrenceEvent recurrenceEvent(Account account, Tag tag) {
        return new RecurrenceEvent(
                "Daily",
                null,
                RecurrenceSchedule.create(
                        true,
                        Instant.parse("2026-07-20T00:00:00Z"),
                        Instant.parse("2026-07-21T00:00:00Z"),
                        null
                ),
                List.of("RRULE:FREQ=DAILY"),
                tag,
                account
        );
    }
}
