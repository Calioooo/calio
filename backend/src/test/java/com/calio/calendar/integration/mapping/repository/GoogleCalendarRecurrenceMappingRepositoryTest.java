package com.calio.calendar.integration.mapping.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
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
import org.springframework.dao.DataIntegrityViolationException;
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
        Tag tag = tagRepository.saveAndFlush(Tag.personalDefault("기타", "#64748B"));
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
                        CanonicalSchedule.recurrenceOverride(
                                Instant.parse("2026-07-21T01:00:00Z"),
                                Instant.parse("2026-07-21T02:00:00Z"),
                                false,
                                "UTC"
                        )
                )
        );
        String externalRecurrenceEventId = "m".repeat(1024);
        String externalRecurrenceOverrideId = "e".repeat(1024);
        GoogleCalendarRecurrenceEventMapping eventMapping = eventMappingRepository.saveAndFlush(
                new GoogleCalendarRecurrenceEventMapping(
                        integration,
                        recurrenceEvent,
                        externalRecurrenceEventId,
                        "a".repeat(64)
                )
        );
        GoogleCalendarRecurrenceOverrideMapping overrideMapping =
                overrideMappingRepository.saveAndFlush(
                        new GoogleCalendarRecurrenceOverrideMapping(
                                eventMapping,
                                recurrenceOverride,
                                externalRecurrenceOverrideId,
                                "a".repeat(64)
                        )
                );

        // when, then
        assertThat(eventMappingRepository.findByRecurrenceEvent_Id(recurrenceEvent.getId()))
                .map(GoogleCalendarRecurrenceEventMapping::getExternalEventId)
                .contains(externalRecurrenceEventId);
        assertThat(overrideMappingRepository.findAllWithRecurrenceEventMappingByExternalEventIds(
                integration.getId(),
                GoogleCalendarRecurrenceEventMapping.PRIMARY_CALENDAR_KEY,
                List.of(externalRecurrenceOverrideId)
        )).extracting(GoogleCalendarRecurrenceOverrideMapping::getId)
                .containsExactly(overrideMapping.getId());
        assertThat(eventMapping.getCreatedAt()).isNotNull();
        assertThat(eventMapping.getUpdatedAt()).isNotNull();
        assertThat(overrideMapping.getCreatedAt()).isNotNull();
        assertThat(overrideMapping.getUpdatedAt()).isNotNull();

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

    @Test
    @DisplayName("동일 provider recurrence event identity의 mapping 중복을 거부한다")
    void givenDuplicateRecurrenceEventProviderIdentity_whenSave_thenRejectsDuplicate() {
        // given
        RecurrenceFixture fixture = recurrenceFixture();
        eventMappingRepository.saveAndFlush(eventMapping(
                fixture,
                fixture.recurrenceEvent(),
                "same-external-id"
        ));
        RecurrenceEvent otherRecurrenceEvent = recurrenceEventRepository.saveAndFlush(
                recurrenceEvent(fixture.account(), fixture.tag())
        );

        // when, then
        assertThatThrownBy(() -> eventMappingRepository.saveAndFlush(eventMapping(
                fixture,
                otherRecurrenceEvent,
                "same-external-id"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("하나의 canonical recurrence event에 두 mapping 연결을 거부한다")
    void givenDuplicateCanonicalRecurrenceEvent_whenSave_thenRejectsSecondMapping() {
        // given
        RecurrenceFixture fixture = recurrenceFixture();
        eventMappingRepository.saveAndFlush(eventMapping(
                fixture,
                fixture.recurrenceEvent(),
                "first-external-id"
        ));

        // when, then
        assertThatThrownBy(() -> eventMappingRepository.saveAndFlush(eventMapping(
                fixture,
                fixture.recurrenceEvent(),
                "second-external-id"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("동일 parent와 external ID의 recurrence override mapping 중복을 거부한다")
    void givenDuplicateRecurrenceOverrideProviderIdentity_whenSave_thenRejectsDuplicate() {
        // given
        RecurrenceFixture fixture = recurrenceFixture();
        GoogleCalendarRecurrenceEventMapping parentMapping =
                eventMappingRepository.saveAndFlush(eventMapping(
                        fixture,
                        fixture.recurrenceEvent(),
                        "recurrence-event-id"
                ));
        RecurrenceEventOverride firstOverride = recurrenceOverride(
                fixture.recurrenceEvent(),
                "2026-07-21T00:00:00Z"
        );
        RecurrenceEventOverride secondOverride = recurrenceOverride(
                fixture.recurrenceEvent(),
                "2026-07-22T00:00:00Z"
        );
        overrideMappingRepository.saveAndFlush(overrideMapping(
                parentMapping,
                firstOverride,
                "same-override-id"
        ));

        // when, then
        assertThatThrownBy(() -> overrideMappingRepository.saveAndFlush(overrideMapping(
                parentMapping,
                secondOverride,
                "same-override-id"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("하나의 canonical recurrence override에 두 mapping 연결을 거부한다")
    void givenDuplicateCanonicalRecurrenceOverride_whenSave_thenRejectsSecondMapping() {
        // given
        RecurrenceFixture fixture = recurrenceFixture();
        GoogleCalendarRecurrenceEventMapping parentMapping =
                eventMappingRepository.saveAndFlush(eventMapping(
                        fixture,
                        fixture.recurrenceEvent(),
                        "recurrence-event-id"
                ));
        RecurrenceEventOverride recurrenceOverride = recurrenceOverride(
                fixture.recurrenceEvent(),
                "2026-07-21T00:00:00Z"
        );
        overrideMappingRepository.saveAndFlush(overrideMapping(
                parentMapping,
                recurrenceOverride,
                "first-override-id"
        ));

        // when, then
        assertThatThrownBy(() -> overrideMappingRepository.saveAndFlush(overrideMapping(
                parentMapping,
                recurrenceOverride,
                "second-override-id"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("recurrence event mapping이 남으면 canonical recurrence event 선행 삭제를 FK가 거부한다")
    void givenReferencedRecurrenceEvent_whenDeleteCanonicalFirst_thenForeignKeyRejects() {
        // given
        RecurrenceFixture fixture = recurrenceFixture();
        eventMappingRepository.saveAndFlush(eventMapping(
                fixture,
                fixture.recurrenceEvent(),
                "recurrence-event-id"
        ));

        // when, then
        assertThatThrownBy(() ->
                recurrenceEventRepository.deleteById(fixture.recurrenceEvent().getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("override mapping이 남아 있으면 canonical recurrence override 선행 삭제를 FK가 거부한다")
    void givenReferencedRecurrenceOverride_whenDeleteCanonicalFirst_thenForeignKeyRejects() {
        // given
        RecurrenceFixture fixture = recurrenceFixture();
        GoogleCalendarRecurrenceEventMapping parentMapping =
                eventMappingRepository.saveAndFlush(eventMapping(
                        fixture,
                        fixture.recurrenceEvent(),
                        "recurrence-event-id"
                ));
        RecurrenceEventOverride recurrenceOverride = recurrenceOverride(
                fixture.recurrenceEvent(),
                "2026-07-21T00:00:00Z"
        );
        overrideMappingRepository.saveAndFlush(overrideMapping(
                parentMapping,
                recurrenceOverride,
                "recurrence-override-id"
        ));

        // when, then
        assertThatThrownBy(() -> recurrenceEventOverrideRepository.delete(recurrenceOverride))
                .isInstanceOf(DataIntegrityViolationException.class);
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

    private RecurrenceFixture recurrenceFixture() {
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(Tag.personalDefault("기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.saveAndFlush(
                recurrenceEvent(account, tag)
        );
        return new RecurrenceFixture(account, tag, integration, recurrenceEvent);
    }

    private GoogleCalendarRecurrenceEventMapping eventMapping(
            RecurrenceFixture fixture,
            RecurrenceEvent recurrenceEvent,
            String externalEventId
    ) {
        return new GoogleCalendarRecurrenceEventMapping(
                fixture.integration(),
                recurrenceEvent,
                externalEventId,
                "a".repeat(64)
        );
    }

    private RecurrenceEventOverride recurrenceOverride(
            RecurrenceEvent recurrenceEvent,
            String originStartAt
    ) {
        return recurrenceEventOverrideRepository.saveAndFlush(
                RecurrenceEventOverride.active(
                        recurrenceEvent,
                        Instant.parse(originStartAt),
                        "Moved",
                        null,
                        CanonicalSchedule.recurrenceOverride(
                                Instant.parse(originStartAt).plusSeconds(3600),
                                Instant.parse(originStartAt).plusSeconds(7200),
                                false,
                                "UTC"
                        )
                )
        );
    }

    private GoogleCalendarRecurrenceOverrideMapping overrideMapping(
            GoogleCalendarRecurrenceEventMapping parentMapping,
            RecurrenceEventOverride recurrenceOverride,
            String externalEventId
    ) {
        return new GoogleCalendarRecurrenceOverrideMapping(
                parentMapping,
                recurrenceOverride,
                externalEventId,
                "a".repeat(64)
        );
    }

    private record RecurrenceFixture(
            Account account,
            Tag tag,
            GoogleCalendarIntegration integration,
            RecurrenceEvent recurrenceEvent
    ) {
    }
}
