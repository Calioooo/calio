package com.calio.calendar.common.time;

import java.time.ZoneId;
import java.util.Set;

public final class IanaTimeZones {

    private static final Set<String> AVAILABLE_IDS = ZoneId.getAvailableZoneIds();

    private IanaTimeZones() {
    }

    public static boolean contains(String timeZone) {
        return AVAILABLE_IDS.contains(timeZone);
    }
}
