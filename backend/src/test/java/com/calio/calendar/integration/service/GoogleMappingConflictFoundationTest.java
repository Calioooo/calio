package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.integration.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.domain.GoogleContentHash;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleMappingConflictFoundationTest {

    private static final NormalizedEventSchedule SCHEDULE = new NormalizedEventSchedule(
            Instant.parse("2026-08-04T00:00:00Z"),
            Instant.parse("2026-08-04T01:00:00Z"),
            false,
            "UTC"
    );

    private final GoogleInboundChangeClassifier classifier =
            new GoogleInboundChangeClassifier();

    @Test
    @DisplayName("provider projection은 type별로 분리된 고정 v1 SHA-256 digest를 만든다")
    void givenSameProviderFields_whenProjectingDifferentTypes_thenDigestsAreSeparated() {
        EventUpsert event = new EventUpsert(
                "provider-id", "etag", Instant.EPOCH,
                "title", null, SCHEDULE);
        RecurrenceEventUpsert master = new RecurrenceEventUpsert(
                "provider-id", "etag", Instant.EPOCH,
                "title", null, SCHEDULE, List.of());

        String eventHash = GoogleProviderContentProjector.event(event);
        String masterHash = GoogleProviderContentProjector.recurrenceMaster(master);

        assertThat(eventHash).matches("v1:[0-9a-f]{64}");
        assertThat(masterHash).matches("v1:[0-9a-f]{64}");
        assertThat(eventHash).isNotEqualTo(masterHash);
    }

    @Test
    @DisplayName("etag과 updated time은 semantic projection에 포함되지 않는다")
    void givenMetadataOnlyDifference_whenProjecting_thenDigestIsUnchanged() {
        EventUpsert first = new EventUpsert(
                "provider-id", "etag-1", Instant.EPOCH,
                "title", "description", SCHEDULE);
        EventUpsert second = new EventUpsert(
                "provider-id", "etag-2", Instant.EPOCH.plusSeconds(60),
                "title", "description", SCHEDULE);

        assertThat(GoogleProviderContentProjector.event(first))
                .isEqualTo(GoogleProviderContentProjector.event(second));
    }

    @Test
    @DisplayName("master scope는 모든 child override scope를 포괄한다")
    void givenMasterAndOverrideScopes_whenComparingCoverage_thenMasterCoversChild() {
        GoogleCalendarEffectiveScope master =
                GoogleCalendarEffectiveScope.recurrenceMaster(1L);
        GoogleCalendarEffectiveScope override =
                GoogleCalendarEffectiveScope.recurrenceOverride(
                        1L, Instant.parse("2026-08-04T00:00:00Z"));

        assertThat(master.covers(override)).isTrue();
        assertThat(override.covers(master)).isFalse();
        assertThat(GoogleCalendarEffectiveScope.decode(
                override.encodedType(), override.encodedKey())).isEqualTo(override);
    }

    @Test
    @DisplayName("digest validator는 canonical lowercase v1 형식 밖의 값을 거부한다")
    void givenNonCanonicalHash_whenValidating_thenRejectsIt() {
        assertThatThrownBy(() -> GoogleContentHash.requireValid("v1:" + "A".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("locked baseline과 양쪽 branch가 모두 다르면 TRUE_CONFLICT로 분류한다")
    void givenDivergedGoogleAndPendingBranch_whenClassifying_thenReturnsTrueConflict() {
        assertThat(classifier.classify(
                hash("baseline"), hash("canonical"), List.of(hash("desired")), hash("google")))
                .isEqualTo(GoogleInboundChangeClassifier.Classification.TRUE_CONFLICT);
    }

    @Test
    @DisplayName("Google이 이전 pending snapshot과 같으면 ALREADY_CONVERGED로 분류한다")
    void givenGoogleMatchesEarlierDesiredSnapshot_whenClassifying_thenAlreadyConverged() {
        String earlierDesired = hash("earlier");
        assertThat(classifier.classify(
                hash("baseline"), hash("canonical"),
                List.of(earlierDesired, hash("latest")), earlierDesired))
                .isEqualTo(GoogleInboundChangeClassifier.Classification.ALREADY_CONVERGED);
    }

    @Test
    @DisplayName("Google과 canonical이 baseline이면 METADATA_ONLY로 분류한다")
    void givenNoSemanticChange_whenClassifying_thenMetadataOnly() {
        String baseline = hash("baseline");

        assertThat(classifier.classify(
                baseline, baseline, List.of(), baseline))
                .isEqualTo(GoogleInboundChangeClassifier.Classification.METADATA_ONLY);
    }

    @Test
    @DisplayName("canonical만 baseline에서 변경되면 CALIO_ONLY로 분류한다")
    void givenOnlyCanonicalChanged_whenClassifying_thenCalioOnly() {
        String baseline = hash("baseline");

        assertThat(classifier.classify(
                baseline, hash("canonical"), List.of(), baseline))
                .isEqualTo(GoogleInboundChangeClassifier.Classification.CALIO_ONLY);
    }

    @Test
    @DisplayName("local branch 없이 Google만 변경되면 GOOGLE_ONLY로 분류한다")
    void givenOnlyGoogleChanged_whenClassifying_thenGoogleOnly() {
        String baseline = hash("baseline");

        assertThat(classifier.classify(
                baseline, baseline, List.of(), hash("google")))
                .isEqualTo(GoogleInboundChangeClassifier.Classification.GOOGLE_ONLY);
    }

    @Test
    @DisplayName("pending Job이 없어도 canonical과 Google이 각각 변경되면 충돌이다")
    void givenCanonicalAndGoogleChanged_whenClassifying_thenTrueConflict() {
        assertThat(classifier.classify(
                hash("baseline"), hash("canonical"), List.of(), hash("google")))
                .isEqualTo(GoogleInboundChangeClassifier.Classification.TRUE_CONFLICT);
    }

    @Test
    @DisplayName("Google이 현재 canonical과 같으면 ALREADY_CONVERGED로 분류한다")
    void givenGoogleMatchesCanonical_whenClassifying_thenAlreadyConverged() {
        String canonical = hash("canonical");

        assertThat(classifier.classify(
                hash("baseline"), canonical, List.of(), canonical))
                .isEqualTo(GoogleInboundChangeClassifier.Classification.ALREADY_CONVERGED);
    }

    @Test
    @DisplayName("provider 삭제도 canonical local branch가 있으면 충돌로 분류한다")
    void givenCanonicalChanged_whenClassifyingDeletion_thenTrueConflict() {
        assertThat(classifier.classifyDeletion(
                hash("baseline"), hash("canonical"), List.of()))
                .isEqualTo(GoogleInboundChangeClassifier.Classification.TRUE_CONFLICT);
    }

    private String hash(String value) {
        return GoogleContentHash.digest("TEST", value);
    }
}
