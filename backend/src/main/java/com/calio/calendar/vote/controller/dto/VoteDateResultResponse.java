package com.calio.calendar.vote.controller.dto;

import java.time.LocalDate;
import java.util.List;

public record VoteDateResultResponse(
        LocalDate date,
        long unavailableCount,
        List<String> unavailableNicknames
) {

    public static VoteDateResultResponse from(
            LocalDate date,
            long unavailableCount,
            List<String> unavailableNicknames
    ) {
        return new VoteDateResultResponse(date, unavailableCount, unavailableNicknames);
    }
}
