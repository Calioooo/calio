package com.calio.calendar.integration.service;

import java.util.Objects;

public final class GoogleCalendarConflictClassifier {

    private GoogleCalendarConflictClassifier() {
    }

    public static Result classify(String baselineHash, String localHash, String providerHash) {
        if (Objects.equals(localHash, providerHash)) {
            return Result.ALREADY_CONVERGED;
        }
        if (baselineHash == null || Objects.equals(localHash, baselineHash)) {
            return Result.GOOGLE_ONLY;
        }
        if (Objects.equals(providerHash, baselineHash)) {
            return Result.CALIO_ONLY;
        }
        return Result.TRUE_CONFLICT;
    }

    public enum Result {
        GOOGLE_ONLY,
        CALIO_ONLY,
        ALREADY_CONVERGED,
        TRUE_CONFLICT
    }
}
