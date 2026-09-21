package com.calio.calendar.vote.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteCandidateDateRange;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

record VoteSubmissionDates(List<LocalDate> values) {

  VoteSubmissionDates {
    values = List.copyOf(values);
  }

  static VoteSubmissionDates of(List<LocalDate> requestedDates) {
    if (requestedDates == null || requestedDates.stream().anyMatch(date -> date == null)) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    return new VoteSubmissionDates(new LinkedHashSet<>(requestedDates).stream().sorted().toList());
  }

  boolean hasDateOutside(VoteCandidateDateRange candidateDateRange) {
    return values.stream().anyMatch(date -> !candidateDateRange.contains(date));
  }
}
