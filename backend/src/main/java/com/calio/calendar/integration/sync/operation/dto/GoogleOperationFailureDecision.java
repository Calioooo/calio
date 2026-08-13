package com.calio.calendar.integration.sync.operation.dto;

public record GoogleOperationFailureDecision(Action action, String reason) {

    public static GoogleOperationFailureDecision skip() {
        return new GoogleOperationFailureDecision(Action.SKIP, null);
    }

    public static GoogleOperationFailureDecision retry(String reason) {
        return new GoogleOperationFailureDecision(Action.RETRY, reason);
    }

    public static GoogleOperationFailureDecision fail(String reason) {
        return new GoogleOperationFailureDecision(Action.FAIL, reason);
    }

    public enum Action {
        SKIP,
        RETRY,
        FAIL
    }
}
