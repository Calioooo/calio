package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.GoogleCalendarSyncTokenExpiredException;
import com.calio.calendar.external.google.GoogleCalendarUnauthorizedException;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.integration.service.GoogleOperationJobPersistenceService.GoogleOperationOwnershipLostException;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.integration.service.GoogleCalendarSyncLeaseService.SyncLease;
import com.calio.calendar.tag.service.TagService;
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
    @DisplayName("INCREMENTAL 410 이후 FULL 재시도는 앞선 INCREMENTAL seen identity를 제거한다")
    void givenExpiredIncrementalCursor_whenSync_thenRetriesInPlaceAndReturnsFullMode() {
        // given
        FakeLeaseService leaseService = new FakeLeaseService("saved-cursor");
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        FakeEventsClient eventsClient = new FakeEventsClient(
                pageWithNextPage("incremental-page-2"),
                new GoogleCalendarSyncTokenExpiredException(),
                terminalPage("full-cursor")
        );
        FakePagePersistenceService pagePersistenceService =
                new FakePagePersistenceService();
        GoogleCalendarSyncService service = new GoogleCalendarSyncService(
                leaseService,
                providerDataService,
                new FakeAccessTokenService(),
                eventsClient,
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
                new FakeOperationJobPersistenceService()
        );

        // when
        GoogleCalendarSyncMode completedMode = executeOwned(service);

        // then
        assertThat(completedMode).isEqualTo(GoogleCalendarSyncMode.FULL);
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
                .containsExactly(new GoogleCalendarSyncRunContext.RecurrenceEventOverrideExternalKey(
                        "full-recurrence-event",
                        "full-override"
                ));
    }

    @Test
    @DisplayName("Events API 401은 access token을 강제 갱신하고 동일 page를 한 번 재시도한다")
    void givenUnauthorizedResponse_whenSync_thenRefreshesAndRetriesOnce() {
        // given
        FakeAccessTokenService accessTokenService = new FakeAccessTokenService();
        FakeEventsClient eventsClient = new FakeEventsClient(
                new GoogleCalendarUnauthorizedException(new RuntimeException()),
                terminalPage("full-cursor")
        );
        GoogleCalendarSyncService service = service(
                new FakeLeaseService(null),
                new FakeProviderDataService(),
                accessTokenService,
                eventsClient,
                new FakePagePersistenceService()
        );

        // when
        GoogleCalendarSyncMode completedMode = executeOwned(service);

        // then
        assertThat(completedMode).isEqualTo(GoogleCalendarSyncMode.FULL);
        assertThat(accessTokenService.forceRefreshCount).isOne();
        assertThat(eventsClient.requestedAccessTokens)
                .containsExactly("access-token", "refreshed-access-token");
    }

    @Test
    @DisplayName("ownership를 잃으면 provider를 호출하지 않고 현재 lease를 해제한다")
    void givenOwnershipLostBeforeExecution_whenExecuteOwned_thenAbandonsRun() {
        // given
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        FakeEventsClient eventsClient = new FakeEventsClient(terminalPage("full-cursor"));
        FakeOperationJobPersistenceService ownershipService =
                new FakeOperationJobPersistenceService();
        ownershipService.failure = new GoogleOperationOwnershipLostException();
        GoogleCalendarSyncService service = new GoogleCalendarSyncService(
                new FakeLeaseService(null),
                providerDataService,
                new FakeAccessTokenService(),
                eventsClient,
                new FakePagePersistenceService(),
                new FakePageNormalizer(),
                ownershipService
        );

        // when, then
        assertThatThrownBy(() -> executeOwned(service))
                .isSameAs(ownershipService.failure);
        assertThat(eventsClient.requestedModes).isEmpty();
        assertThat(providerDataService.releaseCount).isOne();
    }

    @Test
    @DisplayName("Events API가 재시도에도 401이면 partial data cleanup 없이 reconnect required로 종료한다")
    void givenRepeatedUnauthorizedResponses_whenSync_thenRequiresReconnect() {
        // given
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        FakeAccessTokenService accessTokenService = new FakeAccessTokenService();
        GoogleCalendarUnauthorizedException unauthorized =
                new GoogleCalendarUnauthorizedException(new RuntimeException());
        GoogleCalendarSyncService service = service(
                new FakeLeaseService(null),
                providerDataService,
                accessTokenService,
                new FakeEventsClient(unauthorized, unauthorized),
                new FakePagePersistenceService()
        );

        // when, then
        assertThatThrownBy(() -> executeOwned(service))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED));
        assertThat(accessTokenService.forceRefreshCount).isOne();
        assertThat(providerDataService.releaseCount).isOne();
    }

    @Test
    @DisplayName("FULL 실패는 partial provider data를 보존하고 현재 run lease만 해제한다")
    void givenFullSyncFailure_whenSync_thenPreservesPartialProviderData() {
        // given
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        GoogleCalendarSyncService service = service(
                new FakeLeaseService(null),
                providerDataService,
                new FakeEventsClient(new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED)),
                new FakePagePersistenceService()
        );

        // when, then
        assertThatThrownBy(() -> executeOwned(service))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED));
        assertThat(providerDataService.releaseCount).isOne();
    }

    @Test
    @DisplayName("FULL lease 해제 실패는 원본 sync 실패를 유지하고 해제 실패를 suppressed로 남긴다")
    void givenFullSyncAndLeaseReleaseFailures_whenSync_thenPreservesOriginalFailure() {
        // given
        CalioException syncFailure =
                new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED);
        RuntimeException releaseFailure = new RuntimeException("release failed");
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        providerDataService.releaseFailure = releaseFailure;
        GoogleCalendarSyncService service = service(
                new FakeLeaseService(null),
                providerDataService,
                new FakeEventsClient(syncFailure),
                new FakePagePersistenceService()
        );

        // when
        Throwable thrown = catchThrowable(() -> executeOwned(service));

        // then
        assertThat(thrown).isSameAs(syncFailure);
        assertThat(thrown.getSuppressed()).containsExactly(releaseFailure);
    }

    @Test
    @DisplayName("INCREMENTAL 실패는 전체 cleanup 없이 기존 cursor와 앞선 page 결과를 유지한다")
    void givenIncrementalFailure_whenSync_thenOnlyReleasesOwnedLease() {
        // given
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        GoogleCalendarSyncService service = service(
                new FakeLeaseService("saved-cursor"),
                providerDataService,
                new FakeEventsClient(new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED)),
                new FakePagePersistenceService()
        );

        // when, then
        assertThatThrownBy(() -> executeOwned(service))
                .isInstanceOf(CalioException.class);
        assertThat(providerDataService.releaseCount).isOne();
    }

    @Test
    @DisplayName("INCREMENTAL lease 해제 실패는 원본 sync 실패를 유지하고 해제 실패를 suppressed로 남긴다")
    void givenIncrementalAndLeaseReleaseFailures_whenSync_thenPreservesOriginalFailure() {
        // given
        CalioException syncFailure =
                new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED);
        RuntimeException releaseFailure = new RuntimeException("release failed");
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        providerDataService.releaseFailure = releaseFailure;
        GoogleCalendarSyncService service = service(
                new FakeLeaseService("saved-cursor"),
                providerDataService,
                new FakeEventsClient(syncFailure),
                new FakePagePersistenceService()
        );

        // when
        Throwable thrown = catchThrowable(() -> executeOwned(service));

        // then
        assertThat(thrown).isSameAs(syncFailure);
        assertThat(thrown.getSuppressed()).containsExactly(releaseFailure);
    }

    @Test
    @DisplayName("multi-page INCREMENTAL은 중간 page를 반영하고 마지막 page에서 finalize한다")
    void givenMultipleIncrementalPages_whenSync_thenPersistsAndFinalizesByPage() {
        // given
        FakeEventsClient eventsClient = new FakeEventsClient(
                pageWithNextPage("page-2"),
                terminalPage("next-cursor")
        );
        FakePagePersistenceService pagePersistenceService =
                new FakePagePersistenceService();
        GoogleCalendarSyncService service = service(
                new FakeLeaseService("saved-cursor"),
                new FakeProviderDataService(),
                eventsClient,
                pagePersistenceService
        );

        // when
        GoogleCalendarSyncMode completedMode = executeOwned(service);

        // then
        assertThat(completedMode).isEqualTo(GoogleCalendarSyncMode.INCREMENTAL);
        assertThat(eventsClient.requestedPageTokens).containsExactly(null, "page-2");
        assertThat(pagePersistenceService.normalizedPersistCount).isEqualTo(2);
    }

    @Test
    @DisplayName("activation FULL은 page별 commit 후 별도 final transaction에서 cleanup과 cursor를 완료한다")
    void givenActivationFullPages_whenSync_thenPersistsPagesBeforeFinalReconciliation() {
        // given
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        FakePagePersistenceService pagePersistenceService = new FakePagePersistenceService();
        FakeOperationJobPersistenceService ownershipService =
                new FakeOperationJobPersistenceService();
        GoogleCalendarSyncService service = new GoogleCalendarSyncService(
                new FakeLeaseService(null),
                providerDataService,
                new FakeAccessTokenService(),
                new FakeEventsClient(pageWithNextPage("page-2"), terminalPage("next-cursor")),
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
        GoogleCalendarSyncMode completedMode = executeOwned(service);

        // then
        assertThat(completedMode).isEqualTo(GoogleCalendarSyncMode.FULL);
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
                        new GoogleCalendarSyncRunContext.RecurrenceEventOverrideExternalKey(
                                "recurrence-event-1",
                                "override-1"
                        ),
                        new GoogleCalendarSyncRunContext.RecurrenceEventOverrideExternalKey(
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
                Set.of(new GoogleCalendarSyncRunContext.RecurrenceEventOverrideExternalKey(
                        overrideRecurrenceEventId,
                        overrideId
                ))
        );
    }

    private GoogleCalendarSyncMode executeOwned(GoogleCalendarSyncService service) {
        return service.executeOwned(JOB_ID, ACCOUNT_ID, WORKER_TOKEN);
    }

    private GoogleCalendarSyncService service(
            FakeLeaseService leaseService,
            FakeProviderDataService providerDataService,
            FakeEventsClient eventsClient,
            FakePagePersistenceService pagePersistenceService
    ) {
        return service(
                leaseService,
                providerDataService,
                new FakeAccessTokenService(),
                eventsClient,
                pagePersistenceService
        );
    }

    private GoogleCalendarSyncService service(
            FakeLeaseService leaseService,
            FakeProviderDataService providerDataService,
            FakeAccessTokenService accessTokenService,
            FakeEventsClient eventsClient,
            FakePagePersistenceService pagePersistenceService
    ) {
        return new GoogleCalendarSyncService(
                leaseService,
                providerDataService,
                accessTokenService,
                eventsClient,
                pagePersistenceService,
                new FakePageNormalizer(),
                new FakeOperationJobPersistenceService()
        );
    }

    private GoogleCalendarEventPage pageWithNextPage(String nextPageToken) {
        return new GoogleCalendarEventPage(List.of(), nextPageToken, null, "UTC");
    }

    private GoogleCalendarEventPage terminalPage(String nextSyncToken) {
        return new GoogleCalendarEventPage(List.of(), null, nextSyncToken, "UTC");
    }

    private static final class FakeLeaseService extends GoogleCalendarSyncLeaseService {

        private final String nextSyncToken;

        private FakeLeaseService(String nextSyncToken) {
            super(null);
            this.nextSyncToken = nextSyncToken;
        }

        @Override
        public SyncLease acquire(Long accountId, String runId) {
            return new SyncLease(20L, accountId, nextSyncToken, runId);
        }
    }

    private static final class FakeProviderDataService
            extends GoogleCalendarProviderDataService {

        private int releaseCount;
        private int finalizeCount;
        private GoogleCalendarSyncMode finalizedMode;
        private String finalizedCursor;
        private Set<String> finalizedSeenEventIds = Set.of();
        private Set<String> finalizedSeenRecurrenceEventIds = Set.of();
        private Set<GoogleCalendarSyncRunContext.RecurrenceEventOverrideExternalKey>
                finalizedSeenOverrideIds = Set.of();
        private RuntimeException releaseFailure;

        private FakeProviderDataService() {
            super(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    mock(GoogleOperationJobPersistenceService.class),
                    null,
                    null
            );
        }

        @Override
        public void releaseOwnedLease(Long integrationId, String runId) {
            releaseCount++;
            if (releaseFailure != null) {
                throw releaseFailure;
            }
        }

        @Override
        public void finalizeOwnedReconciliation(
                Long jobId,
                Long accountId,
                Long integrationId,
                String workerToken,
                GoogleCalendarSyncMode syncMode,
                Set<String> seenEventIds,
                Set<String> seenRecurrenceEventIds,
                Set<GoogleCalendarSyncRunContext.RecurrenceEventOverrideExternalKey>
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
            super(null, null, null, null);
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

    private static final class FakeOperationJobPersistenceService
            extends GoogleOperationJobPersistenceService {

        private RuntimeException failure;
        private int assertionCount;

        private FakeOperationJobPersistenceService() {
            super(null, null, null, null);
        }

        @Override
        public void renewAndAssertOwned(Long jobId, Long accountId, String workerToken) {
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
                    RestClient.builder()
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
            extends GoogleCalendarEventPagePersistenceService {

        private int normalizedPersistCount;

        private FakePagePersistenceService() {
            super(
                    mock(GoogleCalendarIntegrationRepository.class),
                    mock(GoogleCalendarEventMappingRepository.class),
                    mock(EventRepository.class),
                    mock(AccountRepository.class),
                    mock(TagService.class),
                    mock(GoogleCalendarRecurrenceEventMappingRepository.class),
                    mock(GoogleCalendarRecurrenceOverrideMappingRepository.class),
                    mock(RecurrenceEventRepository.class),
                    mock(RecurrenceEventOverrideRepository.class),
                    mock(GoogleOperationJobPersistenceService.class),
                    mock(GoogleCalendarInboundConflictService.class),
                    mock(GoogleCalendarMappingLockCoordinator.class)
            );
        }

        @Override
        public void persistOwnedNormalizedPage(
                Long jobId,
                Long integrationId,
                Long accountId,
                String workerToken,
                GoogleCalendarNormalizedPage page
        ) {
            normalizedPersistCount++;
        }
    }

    private static final class FakePageNormalizer extends GoogleCalendarPageNormalizer {

        private final Deque<NormalizedPageIdentities> identities = new ArrayDeque<>();

        private FakePageNormalizer(NormalizedPageIdentities... identities) {
            super(null, null, null, null, null, null);
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
            Set<GoogleCalendarSyncRunContext.RecurrenceEventOverrideExternalKey> overrideIds
    ) {
    }
}
