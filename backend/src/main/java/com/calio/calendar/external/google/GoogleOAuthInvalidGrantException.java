package com.calio.calendar.external.google;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;

public class GoogleOAuthInvalidGrantException extends CalioException {
    public GoogleOAuthInvalidGrantException(Throwable cause) {
        super(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED, cause);
    }
}
