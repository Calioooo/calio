package com.calio.calendar.integration.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.GoogleCalendarSyncTokenExpiredException;
import com.calio.calendar.external.google.GoogleCalendarUnauthorizedException;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.service.GoogleCalendarAccessTokenService;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationLeaseService;
import com.calio.calendar.integration.sync.operation.GoogleOperationOwnershipLostException;
import com.calio.calendar.integration.sync.page.GoogleCalendarEventChangeService;
import com.calio.calendar.integration.sync.page.GoogleCalendarPageChangeService;
import com.calio.calendar.integration.sync.page.GoogleCalendarPageOwnership;
import com.calio.calendar.integration.sync.page.GoogleCalendarPageNormalizer;
import com.calio.calendar.integration.sync.page.GoogleCalendarRecurrenceChangeService;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarRecurrenceOverrideExternalKey;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.tag.service.TagQueryService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GoogleCalendarSyncServiceTest {

    private static final long JOB_ID = 30L;
    private static final long ACCOUNT_ID = 10L;
    private static final String WORKER_TOKEN = "worker-token";

    @Test
    @DisplayName("INCREMENTAL 두 번째 page 요청이 410이면 FULL sync를 처음부터 다시 실행하고 이전 처리 결과를 사용하지 않는다")
    void givenExpiredIncrementalSecondPage_whenSync_thenRestartsFullSyncWithoutIncrementalResults() {
        // given
        FakeIntegrationQueryService integrationQueryService =
                new FakeIntegrationQueryService("saved-cursor");
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        FakeEventsClient eventsClient = new FakeEventsClient(
                pageWithNextPageToken("incremental-page-2"), // incremental-sync
                new GoogleCalendarSyncTokenExpiredException(), // sync token expired exception
                terminalPage("full-cursor") // full-sync
        );
        FakePagePersistenceService pagePersistenceService =
                new FakePagePersistenceService();
        FakeAccessTokenService accessTokenService = new FakeAccessTokenService();
        GoogleCalendarSyncService service = new GoogleCalendarSyncService(
                integrationQueryService,
                providerDataService,
                accessTokenService,
                eventRequestService(eventsClient, accessTokenService),
                pagePersistenceService,
                new FakePageNormalizer(
                        identities(
                                "incremental-event",
                                "incremental-recurrence-event",
                                "incremental-override",
                                "incremental-recurrence-event"
                        ),
                        identities(
                                "full-event",
                                "full-recurrence-event",
                                "full-override",
                                "full-recurrence-event"
                        )
                ),
                new FakeOperationLeaseService()
        );

        // when
        synchronize(service);

        // then
        assertThat(eventsClient.requestedModes)
                .containsExactly(
                        GoogleCalendarSyncMode.INCREMENTAL,
                        GoogleCalendarSyncMode.INCREMENTAL,
                        GoogleCalendarSyncMode.FULL
                );
        assertThat(pagePersistenceService.normalizedPersistCount).isEqualTo(2);
        assertThat(providerDataService.finalizeCount).isOne();
        assertThat(providerDataService.finalizedSeenEventIds)
                .containsExactly("full-event");
        assertThat(providerDataService.finalizedSeenRecurrenceEventIds)
                .containsExactly("full-recurrence-event");
        assertThat(providerDataService.finalizedSeenOverrideIds)
                .containsExactly(new GoogleCalendarRecurrenceOverrideExternalKey(
                        "full-recurrence-event",
                        "full-override"
                ));
    }

    @Test
    @DisplayName("Events API 401은 access token을 강제 갱신하고 동일 page 요청을 재시도한다")
    void givenUnauthorizedResponse_whenSync_thenRefreshesAndRetriesOnce() {
        // given
        FakeAccessTokenService accessTokenService = new FakeAccessTokenService();
        FakeEventsClient eventsClient = new FakeEventsClient(
                new GoogleCalendarUnauthorizedException(new RuntimeException()),
                terminalPage("full-cursor")
        );
        GoogleCalendarSyncService service = service(
                new FakeIntegrationQueryService(null),
                new FakeProviderDataService(),
                accessTokenService,
                eventsClient,
                new FakePagePersistenceService()
        );

        // when
        synchronize(service);

        // then
        assertThat(accessTokenService.forceRefreshCount).isOne();
        assertThat(eventsClient.requestedAccessTokens)
                .containsExactly("access-token", "refreshed-access-token");
    }

    @Test
    @DisplayName("operation ownership을 잃으면 provider를 호출하지 않는다")
    void givenOwnershipLostBeforeExecution_whenExecuteOwned_thenAbandonsRun() {
        // given
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        FakeEventsClient eventsClient = new FakeEventsClient(terminalPage("full-cursor"));
        FakeOperationLeaseService ownershipService = new FakeOperationLeaseService();
        ownershipService.failure = new GoogleOperationOwnershipLostException();
        FakeAccessTokenService accessTokenService = new FakeAccessTokenService();
        GoogleCalendarSyncService service = new GoogleCalendarSyncService(
                new FakeIntegrationQueryService(null),
                providerDataService,
                accessTokenService,
                eventRequestService(eventsClient, accessTokenService),
                new FakePagePersistenceService(),
                new FakePageNormalizer(),
                ownershipService
        );

        // when, then
        assertThatThrownBy(() -> synchronize(service))
                .isSameAs(ownershipService.failure);
        assertThat(eventsClient.requestedModes).isEmpty();
    }

    @Test
    @DisplayName("Events API가 재시도에도 401이면 reconnect required로 종료한다")
    void givenRepeatedUnauthorizedResponses_whenSync_thenRequiresReconnect() {
        // given
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        FakeAccessTokenService accessTokenService = new FakeAccessTokenService();
        GoogleCalendarUnauthorizedException unauthorized =
                new GoogleCalendarUnauthorizedException(new RuntimeException());
        GoogleCalendarSyncService service = service(
                new FakeIntegrationQueryService(null),
                providerDataService,
                accessTokenService,
                new FakeEventsClient(unauthorized, unauthorized),
                new FakePagePersistenceService()
        );

        // when, then
        assertThatThrownBy(() -> synchronize(service))
                .isInstanceOfSatisfying(
                        CalioException.class, exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED)
                );
        assertThat(accessTokenService.forceRefreshCount).isOne();
    }

    @Test
    @DisplayName("FULL SYNC의 다음 page가 실패하면 앞선 page를 저장해도 final reconciliation과 cursor 변경을 수행하지 않는다")
    void givenLaterFullSyncPageFailure_whenSync_thenDoesNotFinalizePartialInventory() {
        // given
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        GoogleCalendarSyncService service = service(
                new FakeIntegrationQueryService(null),
                providerDataService,
                new FakeEventsClient(
                        pageWithNextPageToken("page-2"),
                        new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED)
                ),
                new FakePagePersistenceService()
        );

        // when, then
        assertThatThrownBy(() -> synchronize(service))
                .isInstanceOfSatisfying(
                        CalioException.class, exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED)
                );
        assertThat(providerDataService.finalizeCount).isZero();
    }

    @Test
    @DisplayName("INCREMENTAL 시 여러 page를 응답하면 마지막 page 에서 finalize 한다")
    void givenMultipleIncrementalPages_whenSync_thenPersistsAndFinalizesByPage() {
        // given
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        FakeEventsClient eventsClient = new FakeEventsClient(
                pageWithNextPageToken("page-2"),
                terminalPage("next-cursor")
        );
        FakePagePersistenceService pagePersistenceService =
                new FakePagePersistenceService();
        GoogleCalendarSyncService service = service(
                new FakeIntegrationQueryService("saved-cursor"),
                providerDataService,
                eventsClient,
                pagePersistenceService
        );

        // when
        synchronize(service);

        // then
        assertThat(eventsClient.requestedPageTokens).containsExactly(null, "page-2");
        assertThat(pagePersistenceService.normalizedPersistCount).isEqualTo(2);
        assertThat(providerDataService.finalizeCount).isOne();
        assertThat(providerDataService.finalizedMode)
                .isEqualTo(GoogleCalendarSyncMode.INCREMENTAL);
        assertThat(providerDataService.finalizedCursor)
                .isEqualTo("next-cursor");
    }

    @Test
    @DisplayName("FULL SYNC는 page별 저장 후 별도의 트랜잭션에서 cleanup과 cursor를 변경한다")
    void givenActivationFullPages_whenSync_thenPersistsPagesBeforeFinalReconciliation() {
        // given
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        FakePagePersistenceService pagePersistenceService = new FakePagePersistenceService();
        FakeOperationLeaseService ownershipService = new FakeOperationLeaseService();
        FakeAccessTokenService accessTokenService = new FakeAccessTokenService();
        FakeEventsClient eventsClient = new FakeEventsClient(
                pageWithNextPageToken("page-2"),
                terminalPage("next-cursor")
        );
        GoogleCalendarSyncService service = new GoogleCalendarSyncService(
                new FakeIntegrationQueryService(null),
                providerDataService,
                accessTokenService,
                eventRequestService(eventsClient, accessTokenService),
                pagePersistenceService,
                new FakePageNormalizer(
                        identities(
                                "event-1",
                                "recurrence-event-1",
                                "override-1",
                                "recurrence-event-1"
                        ),
                        identities(
                                "event-2",
                                "recurrence-event-2",
                                "override-2",
                                "recurrence-event-2"
                        )
                ),
                ownershipService
        );

        // when
        synchronize(service);

        // then
        assertThat(pagePersistenceService.normalizedPersistCount).isEqualTo(2);
        assertThat(providerDataService.finalizeCount).isOne();
        assertThat(providerDataService.finalizedMode).isEqualTo(GoogleCalendarSyncMode.FULL);
        assertThat(providerDataService.finalizedCursor).isEqualTo("next-cursor");
        assertThat(providerDataService.finalizedSeenEventIds)
                .containsExactlyInAnyOrder("event-1", "event-2");
        assertThat(providerDataService.finalizedSeenRecurrenceEventIds)
                .containsExactlyInAnyOrder("recurrence-event-1", "recurrence-event-2");
        assertThat(providerDataService.finalizedSeenOverrideIds)
                .containsExactlyInAnyOrder(
                        new GoogleCalendarRecurrenceOverrideExternalKey(
                                "recurrence-event-1",
                                "override-1"
                        ),
                        new GoogleCalendarRecurrenceOverrideExternalKey(
                                "recurrence-event-2",
                                "override-2"
                        )
                );
        assertThat(ownershipService.assertionCount).isEqualTo(6);
    }

    private static NormalizedPageIdentities identities(
            String eventId,
            String recurrenceEventId,
            String overrideId,
            String overrideRecurrenceEventId
    ) {
        return new NormalizedPageIdentities(
                Set.of(eventId),
                Set.of(recurrenceEventId),
                Set.of(new GoogleCalendarRecurrenceOverrideExternalKey(
                        overrideRecurrenceEventId,
                        overrideId
                ))
        );
    }

    private void synchronize(GoogleCalendarSyncService service) {
        service.synchronize(JOB_ID, ACCOUNT_ID, WORKER_TOKEN);
    }

    private GoogleCalendarSyncService service(
            FakeIntegrationQueryService integrationQueryService,
            FakeProviderDataService providerDataService,
            FakeEventsClient eventsClient,
            FakePagePersistenceService pagePersistenceService
    ) {
        return service(
                integrationQueryService,
                providerDataService,
                new FakeAccessTokenService(),
                eventsClient,
                pagePersistenceService
        );
    }

    private GoogleCalendarSyncService service(
            FakeIntegrationQueryService integrationQueryService,
            FakeProviderDataService providerDataService,
            FakeAccessTokenService accessTokenService,
            FakeEventsClient eventsClient,
            FakePagePersistenceService pagePersistenceService
    ) {
        return new GoogleCalendarSyncService(
                integrationQueryService,
                providerDataService,
                accessTokenService,
                eventRequestService(eventsClient, accessTokenService),
                pagePersistenceService,
                new FakePageNormalizer(),
                new FakeOperationLeaseService()
        );
    }

    private static GoogleCalendarEventRequestService eventRequestService(
            FakeEventsClient eventsClient,
            FakeAccessTokenService accessTokenService
    ) {
        return new GoogleCalendarEventRequestService(eventsClient, accessTokenService);
    }

    private GoogleCalendarEventPage pageWithNextPageToken(String nextPageToken) {
        return new GoogleCalendarEventPage(List.of(), nextPageToken, null, "UTC");
    }

    private GoogleCalendarEventPage terminalPage(String nextSyncToken) {
        return new GoogleCalendarEventPage(List.of(), null, nextSyncToken, "UTC");
    }

    private static final class FakeIntegrationQueryService
            extends GoogleCalendarIntegrationQueryService {

        private final GoogleCalendarIntegration integration;

        private FakeIntegrationQueryService(String nextSyncToken) {
            super(null);
            integration = mock(GoogleCalendarIntegration.class);
            when(integration.getId()).thenReturn(20L);
            when(integration.getAccountId()).thenReturn(ACCOUNT_ID);
            when(integration.getNextSyncToken()).thenReturn(nextSyncToken);
        }

        @Override
        public GoogleCalendarIntegration getIntegration(Long accountId) {
            return integration;
        }
    }

    private static final class FakeProviderDataService
            extends GoogleCalendarIntegrationDataService {

        private int finalizeCount;
        private GoogleCalendarSyncMode finalizedMode;
        private String finalizedCursor;
        private Set<String> finalizedSeenEventIds = Set.of();
        private Set<String> finalizedSeenRecurrenceEventIds = Set.of();
        private Set<GoogleCalendarRecurrenceOverrideExternalKey>
                finalizedSeenOverrideIds = Set.of();

        private FakeProviderDataService() {
            super(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    mock(GoogleOperationLeaseService.class),
                    mock(GoogleOperationJobService.class),
                    mock(GoogleOperationJobQueryService.class)
            );
        }

        @Override
        public void completeSyncRun(
                Long jobId,
                Long accountId,
                Long integrationId,
                String workerToken,
                GoogleCalendarSyncMode syncMode,
                Set<String> seenEventIds,
                Set<String> seenRecurrenceEventIds,
                Set<GoogleCalendarRecurrenceOverrideExternalKey>
                        seenOverrideIds,
                String nextSyncToken
        ) {
            finalizeCount++;
            finalizedMode = syncMode;
            finalizedCursor = nextSyncToken;
            finalizedSeenEventIds = Set.copyOf(seenEventIds);
            finalizedSeenRecurrenceEventIds = Set.copyOf(seenRecurrenceEventIds);
            finalizedSeenOverrideIds = Set.copyOf(seenOverrideIds);
        }
    }

    private static final class FakeAccessTokenService
            extends GoogleCalendarAccessTokenService {

        private int forceRefreshCount;

        private FakeAccessTokenService() {
            super(null, null, null, null, null,
                    mock(org.springframework.transaction.PlatformTransactionManager.class), null);
        }

        @Override
        public String getAccessToken(Long integrationId) {
            return "access-token";
        }

        @Override
        public String forceRefresh(Long integrationId) {
            forceRefreshCount++;
            return "refreshed-access-token";
        }
    }

    private static final class FakeOperationLeaseService extends GoogleOperationLeaseService {

        private RuntimeException failure;
        private int assertionCount;

        private FakeOperationLeaseService() {
            super(null);
        }

        @Override
        public void extend(Long jobId, Long accountId, String workerToken) {
            assertionCount++;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class FakeEventsClient extends GoogleCalendarEventsClient {

        private final Deque<Object> results = new ArrayDeque<>();
        private final List<GoogleCalendarSyncMode> requestedModes = new ArrayList<>();
        private final List<String> requestedPageTokens = new ArrayList<>();
        private final List<String> requestedAccessTokens = new ArrayList<>();

        private FakeEventsClient(Object... results) {
            super(
                    new GoogleOAuthProperties(),
                    new ObjectMapper(),
                    RestClient.builder().build()
            );
            this.results.addAll(List.of(results));
        }

        @Override
        public GoogleCalendarEventPage listEvents(
                String accessToken,
                GoogleCalendarSyncMode mode,
                String syncToken,
                String pageToken
        ) {
            requestedAccessTokens.add(accessToken);
            requestedModes.add(mode);
            requestedPageTokens.add(pageToken);
            Object result = results.removeFirst();
            if (result instanceof RuntimeException exception) {
                throw exception;
            }
            return (GoogleCalendarEventPage) result;
        }
    }

    private static final class FakePagePersistenceService
            extends GoogleCalendarPageChangeService {

        private int normalizedPersistCount;

        private FakePagePersistenceService() {
            super(
                    mock(GoogleCalendarIntegrationQueryService.class),
                    mock(GoogleCalendarEventMappingQueryService.class),
                    mock(GoogleCalendarEventChangeService.class),
                    mock(GoogleCalendarRecurrenceMappingQueryService.class),
                    mock(AccountQueryService.class),
                    mock(TagQueryService.class),
                    mock(RecurrenceEventQueryService.class),
                    mock(GoogleCalendarRecurrenceChangeService.class),
                    mock(GoogleOperationLeaseService.class)
            );
        }

        @Override
        public void applyNormalizedPage(
                Long integrationId,
                Long accountId,
                GoogleCalendarPageOwnership ownership,
                GoogleCalendarNormalizedPage page
        ) {
            normalizedPersistCount++;
        }
    }

    private static final class FakePageNormalizer extends GoogleCalendarPageNormalizer {

        private final Deque<NormalizedPageIdentities> identities = new ArrayDeque<>();

        private FakePageNormalizer(NormalizedPageIdentities... identities) {
            super(null, null, null, null, null);
            this.identities.addAll(List.of(identities));
        }

        @Override
        public GoogleCalendarNormalizedPage normalize(
                Long integrationId,
                GoogleCalendarEventPage page,
                GoogleCalendarSyncRunContext context
        ) {
            if (!identities.isEmpty()) {
                NormalizedPageIdentities pageIdentities = identities.removeFirst();
                pageIdentities.eventIds().forEach(context::seeEvent);
                pageIdentities.recurrenceEventIds().forEach(context::seeRecurrenceEvent);
                pageIdentities.overrideIds().forEach(identity ->
                        context.seeRecurrenceEventOverride(
                                identity.recurrenceEventExternalId(),
                                identity.overrideExternalEventId()
                        ));
            }
            return new GoogleCalendarNormalizedPage(
                    List.of(),
                    page.nextPageToken(),
                    page.nextSyncToken()
            );
        }
    }

    private record NormalizedPageIdentities(
            Set<String> eventIds,
            Set<String> recurrenceEventIds,
            Set<GoogleCalendarRecurrenceOverrideExternalKey> overrideIds
    ) {
    }
}
