package com.calio.calendar.vote.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

@Embeddable
public class VoteCandidateDateRange {

  private static final int MAX_CANDIDATE_DAYS = 31;

  @Column(name = "candidate_start_date", nullable = false)
  private LocalDate candidateStartDate;

  @Column(name = "candidate_end_date", nullable = false)
  private LocalDate candidateEndDate;

  protected VoteCandidateDateRange() {}

  private VoteCandidateDateRange(LocalDate candidateStartDate, LocalDate candidateEndDate) {
    this.candidateStartDate = candidateStartDate;
    this.candidateEndDate = candidateEndDate;
  }

  public static VoteCandidateDateRange of(
      LocalDate candidateStartDate, LocalDate candidateEndDate) {
    if (candidateStartDate == null
        || candidateEndDate == null
        || !candidateEndDate.isAfter(candidateStartDate)
        || candidateEndDate.isAfter(candidateStartDate.plusDays(MAX_CANDIDATE_DAYS - 1))) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    return new VoteCandidateDateRange(candidateStartDate, candidateEndDate);
  }

  public boolean contains(LocalDate date) {
    return date != null && !date.isBefore(candidateStartDate) && !date.isAfter(candidateEndDate);
  }

  public LocalDate candidateStartDate() {
    return candidateStartDate;
  }

  public LocalDate candidateEndDate() {
    return candidateEndDate;
  }
}
