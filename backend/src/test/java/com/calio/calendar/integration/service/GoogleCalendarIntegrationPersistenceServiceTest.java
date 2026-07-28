package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
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
    private AccountRepository accountRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @Transactional
    @DisplayName("reconnect는 같은 integration row에서 provider data와 이전 lease를 무효화한다")
    void givenConnectedProviderData_whenReconnect_thenReplacesIdentityAndInvalidatesOldRun() {
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
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId())).isEmpty();
        assertThat(eventRepository.findById(importedEvent.getId())).isEmpty();
        assertThat(integrationRepository.extendSyncLease(integration.getId(), "old-run")).isZero();
    }

    @Test
    @DisplayName("disconnect local delete는 mapping, import Event, integration을 함께 제거한다")
    void givenConnectedProviderData_whenDeleteIntegration_thenDeletesProviderDataFirst() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        Event importedEvent = createMappedEvent(account, integration, "external-before-disconnect");

        // when
        persistenceService.deleteByAccountId(account.getId());

        // then
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId())).isEmpty();
        assertThat(eventRepository.findById(importedEvent.getId())).isEmpty();
        assertThat(integrationRepository.findById(integration.getId())).isEmpty();
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
}
