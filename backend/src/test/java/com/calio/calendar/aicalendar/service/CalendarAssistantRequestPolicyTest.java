package com.calio.calendar.aicalendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalendarAssistantRequestPolicyTest {

    private final CalendarAssistantRequestPolicy policy = new CalendarAssistantRequestPolicy();

    @Test
    @DisplayName("availability 요청에 날짜, 시간대, 최소 시간이 빠지면 한 번에 clarification을 반환한다")
    void givenAvailabilityRequestMissingInputs_whenEvaluate_thenReturnsCombinedClarification() {
        // when
        String response = policy.localResponseIfRequired("빈 시간 찾아줘", 14);

        // then
        assertThat(response).contains("날짜 범위").contains("시간대").contains("최소 시간");
    }

    @Test
    @DisplayName("14일을 넘는 명시 기간은 Calendar tool 호출 전 좁은 기간을 요청한다")
    void givenOversizedDateRange_whenEvaluate_thenReturnsRangeClarification() {
        // when
        String response = policy.localResponseIfRequired(
                "2026-07-01부터 2026-07-20까지 일정 알려줘",
                14
        );

        // then
        assertThat(response).contains("최대 14일");
    }

    @Test
    @DisplayName("일정 생성·수정·삭제 요청은 조회 도구 없이 지원 범위를 안내한다")
    void givenCalendarMutationRequest_whenEvaluate_thenReturnsUnsupportedScopeResponse() {
        // when
        String response = policy.localResponseIfRequired("내일 회의 일정 추가해줘", 14);

        // then
        assertThat(response).contains("생성·수정·삭제");
    }
}
