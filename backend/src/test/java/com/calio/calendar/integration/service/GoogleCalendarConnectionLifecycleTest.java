package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
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
import com.calio.calendar.security.TokenEncryptor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-google-lifecycle-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "external.google.oauth.client-id=test-client-id",
        "external.google.oauth.client-secret=test-client-secret",
        "external.google.oauth.redirect-uri=https://example.com/oauth/callback"
})
class GoogleCalendarConnectionLifecycleTest {

    @Autowired
    private GoogleCalendarConnectionService connectionService;

    @MockitoBean
    private GoogleOAuthClient googleOAuthClient;

    @MockitoBean
    private TokenEncryptor tokenEncryptor;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("reconnect는 같은 integration row에서 provider data와 이전 lease를 무효화한다")
    void givenConnectedProviderData_whenReconnect_thenReplacesIdentityAndInvalidatesOldRun() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        Event importedEvent = createMappedEvent(account, integration, "external-before-reconnect");
        preparePreviousSyncState(account.getId(), integration.getId());
        stubGoogleConnection();

        // when
        connectionService.connect(account.getId(), "authorization-code");

        // then
        GoogleCalendarIntegration reconnected = integrationRepository
                .findByAccountId(account.getId())
                .orElseThrow();
        assertThat(reconnected.getId()).isEqualTo(integration.getId());
        assertThat(reconnected.getGoogleSubject()).isEqualTo("new-google-subject");
        assertThat(reconnected.getNextSyncToken()).isNull();
        assertThat(reconnected.getActiveSyncRunId()).isNull();
        assertThat(reconnected.getSyncLeaseExpiresAt()).isNull();
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId())).isEmpty();
        assertThat(eventRepository.findById(importedEvent.getId())).isEmpty();
        assertPreviousLeaseInvalid(integration.getId());
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
        stubGoogleRevocation();

        // when
        connectionService.disconnect(account.getId());

        // then
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId())).isEmpty();
        assertThat(eventRepository.findById(importedEvent.getId())).isEmpty();
        assertThat(integrationRepository.findById(integration.getId())).isEmpty();
    }

    @Test
    @DisplayName("disconnect는 provider recurrence aggregate를 child-first로 지우고 local recurrence는 보존한다")
    void givenProviderAndLocalRecurrence_whenDisconnect_thenDeletesOnlyProviderAggregate() {
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
        stubGoogleRevocation();

        // when
        connectionService.disconnect(account.getId());

        // then
        assertThat(overrideMappingRepository.count()).isZero();
        assertThat(overrideRepository.findById(providerOverride.getOverrideId())).isEmpty();
        assertThat(recurrenceMappingRepository.count()).isZero();
        assertThat(recurrenceEventRepository.findById(providerRecurrence.getId())).isEmpty();
        assertThat(recurrenceEventRepository.findById(localRecurrence.getId())).isPresent();
    }

    private void stubGoogleConnection() {
        when(googleOAuthClient.exchangeAuthorizationCode("authorization-code"))
                .thenReturn(new GoogleTokenResponse(
                        "new-access-token",
                        "new-refresh-token",
                        3600
                ));
        when(googleOAuthClient.fetchUserInfo("new-access-token"))
                .thenReturn(new GoogleUserInfoResponse(
                        "new-google-subject",
                        "new-user@example.com"
                ));
        when(tokenEncryptor.encryptRefreshToken("new-refresh-token"))
                .thenReturn("new-encrypted-refresh-token");
        when(tokenEncryptor.encryptAccessToken("new-access-token"))
                .thenReturn("new-encrypted-access-token");
    }

    private void preparePreviousSyncState(Long accountId, Long integrationId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(integrationRepository.acquireSyncLease(
                    accountId,
                    "cursor-run",
                    300L
            ))
                    .isOne();
            assertThat(integrationRepository.finalizeSync(
                    integrationId,
                    "cursor-run",
                    "saved-cursor"
            )).isOne();
            assertThat(integrationRepository.acquireSyncLease(
                    accountId,
                    "old-run",
                    300L
            ))
                    .isOne();
        });
    }

    private void assertPreviousLeaseInvalid(Long integrationId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(integrationRepository.extendSyncLease(
                        integrationId,
                        "old-run",
                        300L
                ))
                        .isZero());
    }

    private void stubGoogleRevocation() {
        when(tokenEncryptor.decrypt("encrypted-refresh-token"))
                .thenReturn("refresh-token");
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
