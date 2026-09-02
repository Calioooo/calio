package com.calio.calendar.vote.repository;

import java.time.LocalDate;

public record VoteDateCountProjection(LocalDate unavailableDate, long unavailableCount) {
}
