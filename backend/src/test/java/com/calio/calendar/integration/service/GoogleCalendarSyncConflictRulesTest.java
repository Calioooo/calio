package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.integration.domain.GoogleCalendarSyncTarget;
import com.calio.calendar.integration.domain.GoogleContentHash;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarSyncConflictRulesTest {

    private static final NormalizedEventSchedule SCHEDULE = new NormalizedEventSchedule(
            Instant.parse("2026-08-04T00:00:00Z"),
            Instant.parse("2026-08-04T01:00:00Z"),
            false,
            "UTC"
    );

    private final GoogleCalendarChangeDetector changeDetector =
            new GoogleCalendarChangeDetector();

    @Test
    @DisplayName("Google 일정 종류가 다르면 내용이 같아도 서로 다른 해시를 만든다")
    void givenSameContentForDifferentItemTypes_whenHashing_thenHashesAreDifferent() {
        EventUpsert event = new EventUpsert(
                "provider-id", "etag", Instant.EPOCH,
                "title", null, SCHEDULE);
        RecurrenceEventUpsert recurrenceEvent = new RecurrenceEventUpsert(
                "provider-id", "etag", Instant.EPOCH,
                "title", null, SCHEDULE, List.of());

        String eventHash = GoogleCalendarContentHasher.hashEvent(event);
        String recurrenceEventHash =
                GoogleCalendarContentHasher.hashRecurrenceEvent(recurrenceEvent);

        assertThat(eventHash).matches("v1:[0-9a-f]{64}");
        assertThat(recurrenceEventHash).matches("v1:[0-9a-f]{64}");
        assertThat(eventHash).isNotEqualTo(recurrenceEventHash);
    }

    @Test
    @DisplayName("etag과 Google 수정 시각은 일정 내용 해시에 포함하지 않는다")
    void givenOnlyGoogleMetadataChanged_whenHashing_thenContentHashIsUnchanged() {
        EventUpsert first = new EventUpsert(
                "provider-id", "etag-1", Instant.EPOCH,
                "title", "description", SCHEDULE);
        EventUpsert second = new EventUpsert(
                "provider-id", "etag-2", Instant.EPOCH.plusSeconds(60),
                "title", "description", SCHEDULE);

        assertThat(GoogleCalendarContentHasher.hashEvent(first))
                .isEqualTo(GoogleCalendarContentHasher.hashEvent(second));
    }

    @Test
    @DisplayName("반복 일정 대상은 그 일정에 속한 예외 일정 대상을 포함한다")
    void givenRecurrenceEventAndItsOverride_whenCheckingTargets_thenParentIncludesOverride() {
        GoogleCalendarSyncTarget recurrenceEvent =
                GoogleCalendarSyncTarget.recurrenceEvent(1L);
        GoogleCalendarSyncTarget override =
                GoogleCalendarSyncTarget.recurrenceOverride(
                        1L, Instant.parse("2026-08-04T00:00:00Z"));

        assertThat(recurrenceEvent.includes(override)).isTrue();
        assertThat(override.includes(recurrenceEvent)).isFalse();
        assertThat(GoogleCalendarSyncTarget.fromStoredValues(
                override.storedType(), override.storedKey())).isEqualTo(override);
    }

    @Test
    @DisplayName("내용 해시는 소문자 v1 SHA-256 형식만 허용한다")
    void givenNonCanonicalHash_whenValidating_thenRejectsIt() {
        assertThatThrownBy(() -> GoogleContentHash.requireValid("v1:" + "A".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Calio와 Google 내용이 마지막 동기화 이후 각각 바뀌면 충돌이다")
    void givenCalioAndGoogleChanged_whenDetectingUpdate_thenCalioAndGoogleChanged() {
        assertThat(changeDetector.detectUpdate(
                hash("last-synced"), hash("calio"),
                List.of(hash("pending-google-write")), hash("google")))
                .isEqualTo(GoogleCalendarChangeDetector.ChangeType.CALIO_AND_GOOGLE_CHANGED);
    }

    @Test
    @DisplayName("Google 내용이 대기 중인 쓰기 내용과 같으면 이미 일치한 상태다")
    void givenGoogleMatchesPendingWrite_whenDetectingUpdate_thenChangesAlreadyMatch() {
        String pendingGoogleWriteHash = hash("earlier-write");
        assertThat(changeDetector.detectUpdate(
                hash("last-synced"), hash("calio"),
                List.of(pendingGoogleWriteHash, hash("latest-write")),
                pendingGoogleWriteHash))
                .isEqualTo(GoogleCalendarChangeDetector.ChangeType.CONTENT_ALREADY_MATCHES);
    }

    @Test
    @DisplayName("Calio와 Google 내용이 마지막 동기화 내용과 같으면 내용 변경이 없다")
    void givenContentUnchanged_whenDetectingUpdate_thenContentUnchanged() {
        String lastSyncedContentHash = hash("last-synced");

        assertThat(changeDetector.detectUpdate(
                lastSyncedContentHash, lastSyncedContentHash,
                List.of(), lastSyncedContentHash))
                .isEqualTo(GoogleCalendarChangeDetector.ChangeType.CONTENT_UNCHANGED);
    }

    @Test
    @DisplayName("Calio 내용만 마지막 동기화 이후 바뀌면 Calio 변경이다")
    void givenOnlyCalioChanged_whenDetectingUpdate_thenCalioChanged() {
        String lastSyncedContentHash = hash("last-synced");

        assertThat(changeDetector.detectUpdate(
                lastSyncedContentHash, hash("calio"), List.of(), lastSyncedContentHash))
                .isEqualTo(GoogleCalendarChangeDetector.ChangeType.CALIO_CHANGED);
    }

    @Test
    @DisplayName("Google 내용만 마지막 동기화 이후 바뀌면 Google 변경이다")
    void givenOnlyGoogleChanged_whenDetectingUpdate_thenGoogleChanged() {
        String lastSyncedContentHash = hash("last-synced");

        assertThat(changeDetector.detectUpdate(
                lastSyncedContentHash, lastSyncedContentHash,
                List.of(), hash("google")))
                .isEqualTo(GoogleCalendarChangeDetector.ChangeType.GOOGLE_CHANGED);
    }

    @Test
    @DisplayName("대기 중인 작업이 없어도 Calio와 Google 내용이 각각 바뀌면 충돌이다")
    void givenCalioAndGoogleChangedWithoutPendingWrite_whenDetecting_thenCalioAndGoogleChanged() {
        assertThat(changeDetector.detectUpdate(
                hash("last-synced"), hash("calio"), List.of(), hash("google")))
                .isEqualTo(GoogleCalendarChangeDetector.ChangeType.CALIO_AND_GOOGLE_CHANGED);
    }

    @Test
    @DisplayName("Google과 현재 Calio 내용이 같으면 이미 일치한 상태다")
    void givenGoogleMatchesCurrentCalioContent_whenDetecting_thenChangesAlreadyMatch() {
        String currentCalioContentHash = hash("calio");

        assertThat(changeDetector.detectUpdate(
                hash("last-synced"), currentCalioContentHash,
                List.of(), currentCalioContentHash))
                .isEqualTo(GoogleCalendarChangeDetector.ChangeType.CONTENT_ALREADY_MATCHES);
    }

    @Test
    @DisplayName("Google에서 삭제됐지만 Calio 내용이 바뀌었으면 충돌이다")
    void givenCalioChanged_whenDetectingGoogleDeletion_thenCalioAndGoogleChanged() {
        assertThat(changeDetector.detectDeletion(
                hash("last-synced"), hash("calio"), List.of()))
                .isEqualTo(GoogleCalendarChangeDetector.ChangeType.CALIO_AND_GOOGLE_CHANGED);
    }

    private String hash(String value) {
        return GoogleContentHash.digest("TEST", value);
    }
}
