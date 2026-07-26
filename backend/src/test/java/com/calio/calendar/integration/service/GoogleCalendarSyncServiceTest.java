package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.GoogleCalendarSyncTokenExpiredException;
import com.calio.calendar.external.google.GoogleCalendarUnauthorizedException;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.controller.dto.GoogleCalendarSyncResponse;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.integration.service.GoogleCalendarSyncLeaseService.SyncLease;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GoogleCalendarSyncServiceTest {

    @Test
    @DisplayName("INCREMENTAL 410은 동일 lease에서 provider data를 reset하고 FULL로 완료한다")
    void givenExpiredIncrementalCursor_whenSync_thenResetsAndReturnsFullMode() {
        // given
        FakeLeaseService leaseService = new FakeLeaseService("saved-cursor");
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        FakeEventsClient eventsClient = new FakeEventsClient(
                new GoogleCalendarSyncTokenExpiredException(),
                terminalPage("full-cursor")
        );
        FakePagePersistenceService pagePersistenceService =
                new FakePagePersistenceService();
        GoogleCalendarSyncService service = service(
                leaseService,
                providerDataService,
                eventsClient,
                pagePersistenceService
        );

        // when
        GoogleCalendarSyncResponse response = service.sync(10L);

        // then
        assertThat(response.mode()).isEqualTo(GoogleCalendarSyncMode.FULL);
        assertThat(eventsClient.requestedModes)
                .containsExactly(
                        GoogleCalendarSyncMode.INCREMENTAL,
                        GoogleCalendarSyncMode.FULL
                );
        assertThat(providerDataService.resetCount).isOne();
        assertThat(providerDataService.cleanupFullFailureCount).isZero();
        assertThat(pagePersistenceService.finalizeCount).isOne();
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
        GoogleCalendarSyncResponse response = service.sync(10L);

        // then
        assertThat(response.mode()).isEqualTo(GoogleCalendarSyncMode.FULL);
        assertThat(accessTokenService.forceRefreshCount).isOne();
        assertThat(eventsClient.requestedAccessTokens)
                .containsExactly("access-token", "refreshed-access-token");
    }

    @Test
    @DisplayName("Events API가 재시도에도 401이면 reconnect required로 종료하고 FULL partial data를 정리한다")
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
        assertThatThrownBy(() -> service.sync(10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED));
        assertThat(accessTokenService.forceRefreshCount).isOne();
        assertThat(providerDataService.cleanupFullFailureCount).isOne();
    }

    @Test
    @DisplayName("FULL 실패는 partial provider data를 정리하고 현재 run lease를 해제한다")
    void givenFullSyncFailure_whenSync_thenCleansPartialProviderData() {
        // given
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        GoogleCalendarSyncService service = service(
                new FakeLeaseService(null),
                providerDataService,
                new FakeEventsClient(new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED)),
                new FakePagePersistenceService()
        );

        // when, then
        assertThatThrownBy(() -> service.sync(10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED));
        assertThat(providerDataService.resetCount).isOne();
        assertThat(providerDataService.cleanupFullFailureCount).isOne();
        assertThat(providerDataService.releaseCount).isZero();
    }

    @Test
    @DisplayName("FULL cleanup 실패는 원본 sync 실패를 유지하고 cleanup 실패를 suppressed로 남긴다")
    void givenFullSyncAndCleanupFailures_whenSync_thenPreservesOriginalFailure() {
        // given
        CalioException syncFailure =
                new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED);
        RuntimeException cleanupFailure = new RuntimeException("cleanup failed");
        FakeProviderDataService providerDataService = new FakeProviderDataService();
        providerDataService.cleanupFailure = cleanupFailure;
        GoogleCalendarSyncService service = service(
                new FakeLeaseService(null),
                providerDataService,
                new FakeEventsClient(syncFailure),
                new FakePagePersistenceService()
        );

        // when
        Throwable thrown = catchThrowable(() -> service.sync(10L));

        // then
        assertThat(thrown).isSameAs(syncFailure);
        assertThat(thrown.getSuppressed()).containsExactly(cleanupFailure);
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
        assertThatThrownBy(() -> service.sync(10L))
                .isInstanceOf(CalioException.class);
        assertThat(providerDataService.resetCount).isZero();
        assertThat(providerDataService.cleanupFullFailureCount).isZero();
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
        Throwable thrown = catchThrowable(() -> service.sync(10L));

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
        GoogleCalendarSyncResponse response = service.sync(10L);

        // then
        assertThat(response.mode()).isEqualTo(GoogleCalendarSyncMode.INCREMENTAL);
        assertThat(eventsClient.requestedPageTokens).containsExactly(null, "page-2");
        assertThat(pagePersistenceService.persistCount).isOne();
        assertThat(pagePersistenceService.finalizeCount).isOne();
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
                pagePersistenceService
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

        private int resetCount;
        private int cleanupFullFailureCount;
        private int releaseCount;
        private RuntimeException cleanupFailure;
        private RuntimeException releaseFailure;

        private FakeProviderDataService() {
            super(null, null, null);
        }

        @Override
        public boolean resetUnderLease(Long integrationId, String runId) {
            resetCount++;
            return true;
        }

        @Override
        public void cleanupFullFailureAndRelease(Long integrationId, String runId) {
            cleanupFullFailureCount++;
            if (cleanupFailure != null) {
                throw cleanupFailure;
            }
        }

        @Override
        public void releaseOwnedLease(Long integrationId, String runId) {
            releaseCount++;
            if (releaseFailure != null) {
                throw releaseFailure;
            }
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

        private int persistCount;
        private int finalizeCount;

        private FakePagePersistenceService() {
            super(null, null, null, null, null);
        }

        @Override
        public void persistPage(
                Long integrationId,
                Long accountId,
                String runId,
                GoogleCalendarEventPage page
        ) {
            persistCount++;
        }

        @Override
        public void persistLastPageAndFinalize(
                Long integrationId,
                Long accountId,
                String runId,
                GoogleCalendarEventPage page
        ) {
            finalizeCount++;
        }
    }
}
