package com.calio.calendar.integration.sync.page;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarInboundChangeClassifierTest {

    private static final String BASELINE = hash('a');
    private static final String CANONICAL = hash('b');
    private static final String GOOGLE = hash('c');

    private final GoogleCalendarInboundChangeClassifier classifier =
            new GoogleCalendarInboundChangeClassifier();

    @Test
    @DisplayName("Google만 변경되면 provider content 적용으로 분류한다")
    void givenOnlyGoogleChanged_whenClassify_thenReturnsGoogleOnly() {
        assertThat(classifier.classify(BASELINE, BASELINE, List.of(), GOOGLE))
                .isEqualTo(GoogleCalendarInboundChangeClassifier.Change.GOOGLE_ONLY);
    }

    @Test
    @DisplayName("Calio만 변경되고 Google이 baseline이면 Calio content 보존으로 분류한다")
    void givenOnlyCalioChanged_whenGoogleStillEqualsBaseline_thenReturnsCalioOnly() {
        assertThat(classifier.classify(BASELINE, CANONICAL, List.of(), BASELINE))
                .isEqualTo(GoogleCalendarInboundChangeClassifier.Change.CALIO_ONLY);
    }

    @Test
    @DisplayName("Google 내용이 현재 또는 pending Calio 내용과 같으면 이미 수렴한 것으로 분류한다")
    void givenProviderReplayOfCurrentOrPendingContent_whenClassify_thenReturnsAlreadyConverged() {
        assertThat(classifier.classify(BASELINE, CANONICAL, List.of(), CANONICAL))
                .isEqualTo(GoogleCalendarInboundChangeClassifier.Change.ALREADY_CONVERGED);
        assertThat(classifier.classify(BASELINE, CANONICAL, List.of(GOOGLE), GOOGLE))
                .isEqualTo(GoogleCalendarInboundChangeClassifier.Change.ALREADY_CONVERGED);
    }

    @Test
    @DisplayName("Google과 Calio가 baseline에서 서로 다르게 변경되면 충돌로 분류한다")
    void givenBothBranchesChangedToDifferentContent_whenClassify_thenReturnsTrueConflict() {
        assertThat(classifier.classify(BASELINE, CANONICAL, List.of(), GOOGLE))
                .isEqualTo(GoogleCalendarInboundChangeClassifier.Change.TRUE_CONFLICT);
    }

    private static String hash(char value) {
        return String.valueOf(value).repeat(64);
    }
}
