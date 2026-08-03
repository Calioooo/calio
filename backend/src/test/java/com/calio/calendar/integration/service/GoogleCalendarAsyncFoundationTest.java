package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarOperationJob;
import com.calio.calendar.integration.domain.GoogleCalendarOperationStatus;
import com.calio.calendar.integration.domain.GoogleCalendarOperationTrigger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarAsyncFoundationTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    @Test
    @DisplayName("retry tier는 10분, 30분, 1시간, 6시간 이후 6시간으로 고정된다")
    void retryScheduleIsCappedAtSixHoursWithoutTerminalDiscard() {
        assertThat(GoogleCalendarRetryPolicy.next(0).delay()).isEqualTo(Duration.ofMinutes(10));
        assertThat(GoogleCalendarRetryPolicy.next(1).delay()).isEqualTo(Duration.ofMinutes(30));
        assertThat(GoogleCalendarRetryPolicy.next(2).delay()).isEqualTo(Duration.ofHours(1));
        assertThat(GoogleCalendarRetryPolicy.next(3).delay()).isEqualTo(Duration.ofHours(6));
        assertThat(GoogleCalendarRetryPolicy.next(100).delay()).isEqualTo(Duration.ofHours(6));
    }

    @Test
    @DisplayName("typed fingerprint는 recurrence rule 순서와 all-day timezone 표현 차이를 정규화한다")
    void typedFingerprintNormalizesEquivalentRepresentations() {
        GoogleCalendarContentFingerprint fingerprint = new GoogleCalendarContentFingerprint();

        var first = fingerprint.recurrenceMaster(
                "Daily", null, NOW, NOW.plus(Duration.ofDays(1)), true, "Asia/Seoul",
                List.of("RDATE:20260805", "RRULE:FREQ=DAILY")
        );
        var second = fingerprint.recurrenceMaster(
                "Daily", null, NOW, NOW.plus(Duration.ofDays(1)), true, null,
                List.of("RRULE:FREQ=DAILY", "RDATE:20260805")
        );

        assertThat(first.version()).isEqualTo(GoogleCalendarContentFingerprint.VERSION);
        assertThat(first.hash()).isEqualTo(second.hash()).hasSize(64);
    }

    @Test
    @DisplayName("baseline과 양쪽 branch hash는 one-sided, converged, conflict를 구분한다")
    void classifierDistinguishesSemanticBranches() {
        assertThat(GoogleCalendarConflictClassifier.classify("base", "base", "google"))
                .isEqualTo(GoogleCalendarConflictClassifier.Result.GOOGLE_ONLY);
        assertThat(GoogleCalendarConflictClassifier.classify("base", "local", "base"))
                .isEqualTo(GoogleCalendarConflictClassifier.Result.CALIO_ONLY);
        assertThat(GoogleCalendarConflictClassifier.classify("base", "same", "same"))
                .isEqualTo(GoogleCalendarConflictClassifier.Result.ALREADY_CONVERGED);
        assertThat(GoogleCalendarConflictClassifier.classify("base", "local", "google"))
                .isEqualTo(GoogleCalendarConflictClassifier.Result.TRUE_CONFLICT);
    }

    @Test
    @DisplayName("operation identity와 Account sequence는 retry에서도 불변이고 성공 row와 분리된다")
    void operationIdentityAndSequenceRemainStableAcrossRetry() {
        GoogleCalendarIntegration integration = integration();
        long firstSequence = integration.allocateOperationSequence();
        GoogleCalendarOperationJob job = GoogleCalendarOperationJob.sync(
                integration, firstSequence, GoogleCalendarOperationTrigger.MANUAL, NOW
        );
        String operationId = job.getOperationId();

        job.claim("owner");
        job.retryAt(NOW.plus(Duration.ofMinutes(10)), 1);

        assertThat(job.getOperationId()).isEqualTo(operationId);
        assertThat(job.getSequenceNumber()).isEqualTo(firstSequence);
        assertThat(job.getStatus()).isEqualTo(GoogleCalendarOperationStatus.PENDING);
        assertThat(integration.allocateOperationSequence()).isEqualTo(firstSequence + 1);
    }

    private GoogleCalendarIntegration integration() {
        return new GoogleCalendarIntegration(
                7L, "subject", "user@example.com", "refresh", "access",
                NOW.plus(Duration.ofHours(1)), NOW
        );
    }
}
