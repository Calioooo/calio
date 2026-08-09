package com.calio.calendar.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication token is required."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Authentication token is invalid."),
    AUTH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Authentication token is revoked."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Event not found."),
    RECURRENCE_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurrence event not found."),
    RECURRENCE_OCCURRENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurrence occurrence not found."),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "Task not found."),
    COMPLETED_TASK_TITLE_UPDATE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "Completed task title update is not allowed."
    ),
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "Tag not found."),
    GROUP_SPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "Group space not found."),
    GROUP_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "Group owner permission is required."),
    GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Group member not found."),
    GROUP_MEMBER_NICKNAME_CONFLICT(HttpStatus.CONFLICT, "Group member nickname already exists."),
    GROUP_MEMBER_REJOIN_INVITATION_REQUIRED(
            HttpStatus.CONFLICT,
            "A newer group invitation is required to rejoin."
    ),
    GROUP_OWNER_TRANSFER_REQUIRED(
            HttpStatus.CONFLICT,
            "Group ownership must be transferred before leaving."
    ),
    GROUP_OWNER_TRANSFER_INVALID(HttpStatus.CONFLICT, "Group owner transfer target is invalid."),
    GROUP_OWNER_CANNOT_BE_REMOVED(HttpStatus.CONFLICT, "Group owner cannot be removed."),
    GROUP_INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Group invitation not found."),
    GROUP_INVITATION_EXPIRED(HttpStatus.GONE, "Group invitation expired."),
    GROUP_INVITATION_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Group invitation generation failed."
    ),
    GROUP_INVITATION_ISSUE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Group invitation issue failed."
    ),
    GROUP_INVITATION_CLEANUP_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Group invitation cleanup failed."
    ),
    DEFAULT_TAG_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "Default tag not found."),
    INVALID_TAG_COLOR_CODE(HttpStatus.BAD_REQUEST, "Invalid tag color code."),
    RECURRENCE_UPDATE_TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "Recurrence update time range invalid."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "Invalid time range."),
    INVALID_ALL_DAY_SCHEDULE(HttpStatus.BAD_REQUEST, "Invalid all-day event schedule."),
    EVENT_QUERY_RANGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "Event query range is too large."),
    INVALID_RECURRENCE_SCHEDULE(HttpStatus.BAD_REQUEST, "Invalid recurrence schedule."),
    INVALID_TIME_ZONE(HttpStatus.BAD_REQUEST, "Invalid time zone."),
    INVALID_RECURRENCE_RULE(HttpStatus.BAD_REQUEST, "Invalid recurrence rule."),
    RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "Recurrence occurrence limit exceeded."
    ),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed."),
    GOOGLE_CALENDAR_AUTHORIZATION_CODE_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "Google Calendar authorization code is required."
    ),
    GOOGLE_CALENDAR_CONFIGURATION_MISSING(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Google Calendar integration configuration is missing."
    ),
    GOOGLE_TOKEN_EXCHANGE_FAILED(HttpStatus.BAD_GATEWAY, "Google token exchange failed."),
    GOOGLE_TOKEN_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "Google token response is invalid."),
    GOOGLE_USER_INFO_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "Google user info fetch failed."),
    GOOGLE_USER_INFO_INVALID(HttpStatus.BAD_GATEWAY, "Google user info response is invalid."),
    GOOGLE_TOKEN_REVOKE_FAILED(HttpStatus.BAD_GATEWAY, "Google token revoke failed."),
    GOOGLE_TOKEN_ENCRYPTION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Google token encryption failed."
    ),
    GOOGLE_CALENDAR_INTEGRATION_SAVE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Google Calendar integration save failed."
    ),
    GOOGLE_CALENDAR_NOT_CONNECTED(HttpStatus.CONFLICT, "Google Calendar is not connected."),
    GOOGLE_CALENDAR_RECONNECT_REQUIRED(
            HttpStatus.CONFLICT,
            "Google Calendar reconnection is required."
    ),
    GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID(
            HttpStatus.BAD_GATEWAY,
            "Google Calendar event response is invalid."
    ),
    GOOGLE_CALENDAR_SYNC_FAILED(HttpStatus.BAD_GATEWAY, "Google Calendar sync failed."),
    GOOGLE_CALENDAR_SYNC_TOKEN_MISSING(
            HttpStatus.BAD_GATEWAY,
            "Google Calendar sync token is missing."
    ),
    GOOGLE_CALENDAR_SYNC_CONFLICT(HttpStatus.CONFLICT, "Google Calendar sync is already running."),
    EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED(
            HttpStatus.CONFLICT,
            "External event mutation is not supported."
    ),

    HOLIDAY_API_CONFIGURATION_MISSING(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Holiday API configuration is missing."
    ),
    EXTERNAL_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "External API is temporarily unavailable."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
