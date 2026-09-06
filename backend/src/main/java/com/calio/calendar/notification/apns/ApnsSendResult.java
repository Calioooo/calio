package com.calio.calendar.notification.apns;

public record ApnsSendResult(ApnsSendResultType type, String requestId, String reason) {
    public static ApnsSendResult configurationFailure(String reason) { return new ApnsSendResult(ApnsSendResultType.CONFIGURATION_FAILURE, null, reason); }
}
