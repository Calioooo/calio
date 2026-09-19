package com.calio.calendar.singleevent.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record SingleEventTitle(
        @Column(name = "title", nullable = false, length = SingleEventTitle.MAX_LENGTH) String value
) {

    public static final int MAX_LENGTH = 255;

    public SingleEventTitle {
        if (value == null || value.length() > MAX_LENGTH) {
            throw new CalioException(ErrorCode.INVALID_EVENT_TITLE);
        }
    }
}
