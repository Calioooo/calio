package com.calio.calendar.vote.repository;

import java.time.LocalDate;

public record VoteDateNicknameProjection(LocalDate unavailableDate, String nickname) {
}
