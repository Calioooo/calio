package com.calio.calendar.integration.sync.page;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarContentReconciliationPolicyTest {

    private static final String BASELINE = hash('a');
    private static final String CANONICAL = hash('b');
    private static final String GOOGLE = hash('c');

    private final GoogleCalendarContentReconciliationPolicy reconciliationPolicy =
            new GoogleCalendarContentReconciliationPolicy();

    @Test
    @DisplayName("Google만 변경되면 provider content 적용으로 결정한다")
    void givenOnlyGoogleChanged_whenDecide_thenReturnsGoogleOnly() {
        assertThat(reconciliationPolicy.decide(BASELINE, BASELINE, List.of(), GOOGLE))
                .isEqualTo(GoogleCalendarContentReconciliationDecision.GOOGLE_ONLY);
    }

    @Test
    @DisplayName("Calio만 변경되고 Google이 baseline이면 Calio content 보존으로 결정한다")
    void givenOnlyCalioChanged_whenGoogleStillEqualsBaseline_thenReturnsCalioOnly() {
        assertThat(reconciliationPolicy.decide(BASELINE, CANONICAL, List.of(), BASELINE))
                .isEqualTo(GoogleCalendarContentReconciliationDecision.CALIO_ONLY);
    }

    @Test
    @DisplayName("Google 내용이 현재 또는 pending Calio 내용과 같으면 이미 수렴한 것으로 결정한다")
    void givenProviderReplayOfCurrentOrPendingContent_whenDecide_thenReturnsAlreadyConverged() {
        assertThat(reconciliationPolicy.decide(BASELINE, CANONICAL, List.of(), CANONICAL))
                .isEqualTo(GoogleCalendarContentReconciliationDecision.ALREADY_CONVERGED);
        assertThat(reconciliationPolicy.decide(BASELINE, CANONICAL, List.of(GOOGLE), GOOGLE))
                .isEqualTo(GoogleCalendarContentReconciliationDecision.ALREADY_CONVERGED);
    }

    @Test
    @DisplayName("Google과 Calio가 baseline에서 서로 다르게 변경되면 충돌로 결정한다")
    void givenBothBranchesChangedToDifferentContent_whenDecide_thenReturnsTrueConflict() {
        assertThat(reconciliationPolicy.decide(BASELINE, CANONICAL, List.of(), GOOGLE))
                .isEqualTo(GoogleCalendarContentReconciliationDecision.TRUE_CONFLICT);
    }

    private static String hash(char value) {
        return String.valueOf(value).repeat(64);
    }
}
