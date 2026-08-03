package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarIntegrationState;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
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
        "spring.datasource.url=jdbc:h2:mem:calendar-google-lifecycle-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GoogleCalendarIntegrationPersistenceServiceTest {

    @Autowired
    private GoogleCalendarIntegrationPersistenceService persistenceService;

    @Autowired
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private GoogleCalendarEventMappingRepository mappingRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository;

    @Autowired
    private GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository;

    @Autowired
    private RecurrenceEventRepository recurrenceEventRepository;

    @Autowired
    private RecurrenceEventOverrideRepository overrideRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @Transactional
    @DisplayName("reconnect는 retained mapping을 보존하고 cursor와 이전 lease를 무효화한다")
    void givenConnectedProviderData_whenReconnect_thenRetainsIdentityAndInvalidatesOldRun() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        Event importedEvent = createMappedEvent(account, integration, "external-before-reconnect");
        assertThat(integrationRepository.acquireSyncLease(account.getId(), "cursor-run")).isOne();
        assertThat(integrationRepository.finalizeSync(
                integration.getId(),
                "cursor-run",
                "saved-cursor"
        )).isOne();
        assertThat(integrationRepository.acquireSyncLease(account.getId(), "old-run")).isOne();

        // when
        GoogleCalendarIntegration reconnected = persistenceService.saveOrReplace(
                account.getId(),
                "new-google-subject",
                "new-user@example.com",
                "new-encrypted-refresh-token",
                "new-encrypted-access-token",
                Instant.parse("2026-07-01T03:00:00Z"),
                Instant.parse("2026-07-01T02:00:00Z")
        );

        // then
        assertThat(reconnected.getId()).isEqualTo(integration.getId());
        assertThat(reconnected.getGoogleSubject()).isEqualTo("new-google-subject");
        assertThat(reconnected.getNextSyncToken()).isNull();
        assertThat(reconnected.getActiveSyncRunId()).isNull();
        assertThat(reconnected.getSyncLeaseExpiresAt()).isNull();
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId()))
                .containsExactly(importedEvent.getId());
        assertThat(eventRepository.findById(importedEvent.getId())).isPresent();
        assertThat(integrationRepository.extendSyncLease(integration.getId(), "old-run")).isZero();
    }

    @Test
    @DisplayName("disconnect는 integration과 mapping identity를 retained 상태로 보존한다")
    void givenConnectedProviderData_whenDisconnect_thenRetainsProviderIdentity() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        Event importedEvent = createMappedEvent(account, integration, "external-before-disconnect");

        // when
        persistenceService.deleteByAccountId(account.getId());

        // then
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId()))
                .containsExactly(importedEvent.getId());
        assertThat(eventRepository.findById(importedEvent.getId())).isPresent();
        GoogleCalendarIntegration disconnected = integrationRepository
                .findById(integration.getId()).orElseThrow();
        assertThat(disconnected.getState()).isEqualTo(GoogleCalendarIntegrationState.DISCONNECTED);
        assertThat(disconnected.getEncryptedRefreshToken()).isNull();
        assertThat(disconnected.getEncryptedAccessToken()).isNull();
    }

    @Test
    @DisplayName("disconnect는 provider recurrence aggregate와 local recurrence를 모두 보존한다")
    void givenProviderAndLocalRecurrence_whenDisconnect_thenRetainsMappingsAndCanonicalData() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        Tag defaultTag = tagRepository.saveAndFlush(
                new Tag(TagType.DEFAULT, "기타", "#64748B")
        );
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        RecurrenceEvent providerRecurrence = recurrenceEventRepository.saveAndFlush(
                recurrenceEvent(account, defaultTag, "Provider")
        );
        GoogleCalendarRecurrenceEventMapping parentMapping =
                recurrenceMappingRepository.saveAndFlush(new GoogleCalendarRecurrenceEventMapping(
                        integration, providerRecurrence, "master-1", null, null
                ));
        RecurrenceEventOverride providerOverride = overrideRepository.saveAndFlush(
                RecurrenceEventOverride.deleted(
                        providerRecurrence,
                        Instant.parse("2026-07-02T09:00:00Z"),
                        Instant.parse("2026-07-02T08:00:00Z")
                )
        );
        overrideMappingRepository.saveAndFlush(new GoogleCalendarRecurrenceOverrideMapping(
                parentMapping, providerOverride, "exception-1", null, null
        ));
        RecurrenceEvent localRecurrence = recurrenceEventRepository.saveAndFlush(
                recurrenceEvent(account, defaultTag, "Local")
        );

        // when
        persistenceService.deleteByAccountId(account.getId());

        // then
        assertThat(overrideMappingRepository.count()).isOne();
        assertThat(overrideRepository.findById(providerOverride.getOverrideId())).isPresent();
        assertThat(recurrenceMappingRepository.count()).isOne();
        assertThat(recurrenceEventRepository.findById(providerRecurrence.getId())).isPresent();
        assertThat(recurrenceEventRepository.findById(localRecurrence.getId())).isPresent();
    }

    private Event createMappedEvent(
            Account account,
            GoogleCalendarIntegration integration,
            String externalEventId
    ) {
        Tag fallbackTag = tagRepository.saveAndFlush(
                new Tag(TagType.DEFAULT, "기타", "#64748B")
        );
        Event event = eventRepository.saveAndFlush(new Event(
                "Imported",
                null,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T01:00:00Z"),
                false,
                "UTC",
                null,
                fallbackTag,
                account
        ));
        mappingRepository.saveAndFlush(new GoogleCalendarEventMapping(
                integration,
                event,
                externalEventId,
                null,
                null
        ));
        return event;
    }

    private GoogleCalendarIntegration integration(Long accountId) {
        return new GoogleCalendarIntegration(
                accountId,
                "google-subject",
                "user@example.com",
                "encrypted-refresh-token",
                "encrypted-access-token",
                Instant.parse("2026-07-01T01:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z")
        );
    }

    private RecurrenceEvent recurrenceEvent(Account account, Tag tag, String title) {
        return new RecurrenceEvent(
                title,
                null,
                RecurrenceSchedule.create(
                        false,
                        Instant.parse("2026-07-01T09:00:00Z"),
                        Instant.parse("2026-07-01T10:00:00Z"),
                        "UTC"
                ),
                List.of("RRULE:FREQ=DAILY"),
                tag,
                account
        );
    }
}
