package com.calio.calendar.integration.sync.page;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleCalendarInboundChangeClassifierTest {

    private static final String BASELINE = hash('a');
    private static final String CANONICAL = hash('b');
    private static final String GOOGLE = hash('c');

    private final GoogleCalendarInboundChangeClassifier classifier =
            new GoogleCalendarInboundChangeClassifier();

    @Test
    void givenOnlyGoogleChanged_whenClassify_thenReturnsGoogleOnly() {
        assertThat(classifier.classify(BASELINE, BASELINE, List.of(), GOOGLE))
                .isEqualTo(GoogleCalendarInboundChangeClassifier.Change.GOOGLE_ONLY);
    }

    @Test
    void givenOnlyCalioChanged_whenGoogleStillEqualsBaseline_thenReturnsCalioOnly() {
        assertThat(classifier.classify(BASELINE, CANONICAL, List.of(), BASELINE))
                .isEqualTo(GoogleCalendarInboundChangeClassifier.Change.CALIO_ONLY);
    }

    @Test
    void givenProviderReplayOfCurrentOrPendingContent_whenClassify_thenReturnsAlreadyConverged() {
        assertThat(classifier.classify(BASELINE, CANONICAL, List.of(), CANONICAL))
                .isEqualTo(GoogleCalendarInboundChangeClassifier.Change.ALREADY_CONVERGED);
        assertThat(classifier.classify(BASELINE, CANONICAL, List.of(GOOGLE), GOOGLE))
                .isEqualTo(GoogleCalendarInboundChangeClassifier.Change.ALREADY_CONVERGED);
    }

    @Test
    void givenBothBranchesChangedToDifferentContent_whenClassify_thenReturnsTrueConflict() {
        assertThat(classifier.classify(BASELINE, CANONICAL, List.of(), GOOGLE))
                .isEqualTo(GoogleCalendarInboundChangeClassifier.Change.TRUE_CONFLICT);
    }

    private static String hash(char value) {
        return "v1:" + String.valueOf(value).repeat(64);
    }
}
