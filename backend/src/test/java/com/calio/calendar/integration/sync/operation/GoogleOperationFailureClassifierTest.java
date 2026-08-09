package com.calio.calendar.integration.sync.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.operation.dto.GoogleOperationFailureDecision;
import com.calio.calendar.integration.sync.operation.dto.GoogleOperationFailureDecision.Action;
import java.sql.SQLTransientException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

class GoogleOperationFailureClassifierTest {

    private final GoogleOperationFailureClassifier classifier =
            new GoogleOperationFailureClassifier();

    @Test
    @DisplayName("operation ownership 상실은 상태 변경 없이 skip한다")
    void givenOwnershipLost_whenClassify_thenSkips() {
        // when
        GoogleOperationFailureDecision decision = classifier.classify(
                new GoogleOperationOwnershipLostException()
        );

        // then
        assertThat(decision.action()).isEqualTo(Action.SKIP);
        assertThat(decision.reason()).isNull();
    }

    @Test
    @DisplayName("Spring의 일시적인 DB 오류는 retry한다")
    void givenTransientDataAccessFailure_whenClassify_thenRetries() {
        // when
        GoogleOperationFailureDecision decision = classifier.classify(
                new TransientDataAccessResourceException("temporary")
        );

        // then
        assertThat(decision.action()).isEqualTo(Action.RETRY);
        assertThat(decision.reason())
                .isEqualTo(TransientDataAccessResourceException.class.getSimpleName());
    }

    @Test
    @DisplayName("일시적인 SQL 오류가 있으면 retry한다")
    void givenNestedTransientSqlFailure_whenClassify_thenRetries() {
        // given
        RuntimeException failure = new RuntimeException(
                "database failure",
                new SQLTransientException("temporary")
        );

        // when
        GoogleOperationFailureDecision decision = classifier.classify(failure);

        // then
        assertThat(decision.action()).isEqualTo(Action.RETRY);
        assertThat(decision.reason()).isEqualTo(SQLTransientException.class.getSimpleName());
    }

    @Test
    @DisplayName("재연결이 필요한 Google 오류는 fail 처리한다")
    void givenPermanentCalioFailure_whenClassify_thenFails() {
        // when
        GoogleOperationFailureDecision decision = classifier.classify(
                new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED)
        );

        // then
        assertThat(decision.action()).isEqualTo(Action.FAIL);
        assertThat(decision.reason())
                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED.name());
    }

    @Test
    @DisplayName("원인이 있는 Google Sync 실패는 root cause를 포함해 retry한다")
    void givenCausedSyncFailure_whenClassify_thenRetriesWithCause() {
        // when
        GoogleOperationFailureDecision decision = classifier.classify(
                new CalioException(
                        ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED,
                        new IllegalStateException("provider failure")
                )
        );

        // then
        assertThat(decision.action()).isEqualTo(Action.RETRY);
        assertThat(decision.reason()).isEqualTo(
                ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED.name() + ":IllegalStateException"
        );
    }

    @Test
    @DisplayName("분류되지 않은 내부 오류는 fail 처리한다")
    void givenUnknownFailure_whenClassify_thenFailsAsInternalError() {
        // when
        GoogleOperationFailureDecision decision = classifier.classify(
                new IllegalStateException("unknown")
        );

        // then
        assertThat(decision.action()).isEqualTo(Action.FAIL);
        assertThat(decision.reason()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.name());
    }
}
