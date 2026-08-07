package com.calio.calendar.integration.service.dto;

public record GoogleOperationFailureDecision(Action action, String reason) {

    public static GoogleOperationFailureDecision abandon() {
        return new GoogleOperationFailureDecision(Action.ABANDON, null);
    }

    public static GoogleOperationFailureDecision retry(String reason) {
        return new GoogleOperationFailureDecision(Action.RETRY, reason);
    }

    public static GoogleOperationFailureDecision terminal(String reason) {
        return new GoogleOperationFailureDecision(Action.TERMINAL, reason);
    }

    public enum Action {
        ABANDON,
        RETRY,
        TERMINAL
    }
}
