package com.calio.calendar.exception;

public class EventNotFoundException extends BusinessException {

    public EventNotFoundException(Long eventId) {
        super(ErrorCode.EVENT_NOT_FOUND, "Event not found: " + eventId);
    }
}
