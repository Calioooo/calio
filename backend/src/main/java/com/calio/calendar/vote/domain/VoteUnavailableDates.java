package com.calio.calendar.vote.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

public record VoteUnavailableDates(List<LocalDate> values) {

  public VoteUnavailableDates {
    values = List.copyOf(values);
  }

  public static VoteUnavailableDates of(List<LocalDate> requestedDates) {
    if (requestedDates == null || requestedDates.stream().anyMatch(date -> date == null)) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    return new VoteUnavailableDates(new LinkedHashSet<>(requestedDates).stream().sorted().toList());
  }

  public boolean hasDateOutside(VoteCandidateDateRange candidateDateRange) {
    return values.stream().anyMatch(date -> !candidateDateRange.contains(date));
  }
}
