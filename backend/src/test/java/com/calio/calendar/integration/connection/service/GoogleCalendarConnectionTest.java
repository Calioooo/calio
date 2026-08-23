package com.calio.calendar.integration.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegrationState;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.repository.GoogleOperationJobRepository;
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
class GoogleCalendarConnectionTest {

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

    @Autowired
    private GoogleOperationJobRepository operationJobRepository;

    @Test
    @DisplayName("같은 Google subject 재연결은 기존 mapping과 canonical 일정을 보존하고 cursor만 초기화한다")
    void givenConnectedProviderData_whenReconnect_thenRetainsIdentityAndInvalidatesOldRun() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        Event importedEvent = createMappedEvent(account, integration, "external-before-reconnect");
        preparePreviousSyncState(integration.getId());
        stubGoogleConnection();

        // when
        connectionService.connect(account.getId(), "authorization-code");

        // then
        GoogleCalendarIntegration reconnected = integrationRepository
                .findByAccountId(account.getId())
                .orElseThrow();
        assertThat(reconnected.getId()).isEqualTo(integration.getId());
        assertThat(reconnected.getGoogleSubject()).isEqualTo("google-subject");
        assertThat(reconnected.getNextSyncToken()).isNull();
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId()))
                .containsExactly(importedEvent.getId());
        assertThat(eventRepository.findById(importedEvent.getId())).isPresent();
        assertThat(operationJobRepository.findAll())
                .anyMatch(job -> job.getIntegrationId().equals(integration.getId()));
        GoogleOperationJob reconnectJob = operationJobRepository.findAll().getFirst();
        assertThat(reconnectJob.getIntegrationId()).isEqualTo(integration.getId());
        assertThat(reconnectJob.getKind()).isEqualTo(GoogleOperationJob.SYNC_KIND);
    }

    @Test
    @DisplayName("SYNC_ERROR Integration은 같은 Google subject 재연결로만 CONNECTED로 복구한다")
    void givenSyncErrorIntegration_whenReconnectWithSameSubject_thenResumesAndEnqueuesFullSync() {
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(integration(account.getId()));
        integration.markSyncError("GOOGLE_CALENDAR_RECONNECT_REQUIRED", Instant.parse("2026-08-24T00:00:00Z"));
        integrationRepository.saveAndFlush(integration);
        stubGoogleConnection();

        connectionService.connect(account.getId(), "authorization-code");

        GoogleCalendarIntegration reconnected = integrationRepository.findById(integration.getId()).orElseThrow();
        assertThat(reconnected.getState()).isEqualTo(com.calio.calendar.integration.connection.domain.GoogleCalendarIntegrationState.CONNECTED);
        assertThat(reconnected.getSyncErrorReason()).isNull();
        assertThat(operationJobRepository.findAll())
                .anyMatch(job -> job.getIntegrationId().equals(integration.getId()));
    }

    @Test
    @DisplayName("disconnect시 mapping과 canonical 일정을 보존하고 Integration을 DISCONNECTED로 전환한다")
    void givenConnectedProviderData_whenDisconnect_thenRetainsProviderData() {
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
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId()))
                .containsExactly(importedEvent.getId());
        assertThat(eventRepository.findById(importedEvent.getId())).isPresent();
        GoogleCalendarIntegration disconnected = integrationRepository.findById(integration.getId())
                .orElseThrow();
        assertThat(disconnected.getState()).isEqualTo(GoogleCalendarIntegrationState.DISCONNECTED);
        assertThat(disconnected.getEncryptedRefreshToken()).isNull();
        assertThat(disconnected.getEncryptedAccessToken()).isNull();
        assertThat(disconnected.getNextSyncToken()).isNull();
    }

    @Test
    @DisplayName("disconnect시 provider recurrence mapping과 canonical aggregate를 보존한다")
    void givenProviderAndLocalRecurrence_whenDisconnect_thenRetainsBothAggregates() {
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
                        integration, providerRecurrence, "master-1", "a".repeat(64)
                ));
        RecurrenceEventOverride providerOverride = overrideRepository.saveAndFlush(
                RecurrenceEventOverride.deleted(
                        providerRecurrence,
                        Instant.parse("2026-07-02T09:00:00Z"),
                        Instant.parse("2026-07-02T08:00:00Z")
                )
        );
        overrideMappingRepository.saveAndFlush(new GoogleCalendarRecurrenceOverrideMapping(
                parentMapping, providerOverride, "exception-1", "a".repeat(64)
        ));
        RecurrenceEvent localRecurrence = recurrenceEventRepository.saveAndFlush(
                recurrenceEvent(account, defaultTag, "Local")
        );
        stubGoogleRevocation();

        // when
        connectionService.disconnect(account.getId());

        // then
        assertThat(overrideMappingRepository.count()).isOne();
        assertThat(overrideRepository.findById(providerOverride.getOverrideId())).isPresent();
        assertThat(recurrenceMappingRepository.count()).isOne();
        assertThat(recurrenceEventRepository.findById(providerRecurrence.getId())).isPresent();
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
                        "google-subject",
                        "new-user@example.com"
                ));
        when(tokenEncryptor.encryptRefreshToken("new-refresh-token"))
                .thenReturn("new-encrypted-refresh-token");
        when(tokenEncryptor.encryptAccessToken("new-access-token"))
                .thenReturn("new-encrypted-access-token");
    }

    private void preparePreviousSyncState(Long integrationId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(integrationRepository.updateNextSyncToken(
                    integrationId,
                    "saved-cursor"
            )).isOne();
        });
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
                "a".repeat(64)
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
