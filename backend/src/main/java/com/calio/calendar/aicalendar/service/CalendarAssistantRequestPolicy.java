package com.calio.calendar.aicalendar.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CalendarAssistantRequestPolicy {

    private static final Pattern ISO_DATE_RANGE = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})\\s*(?:부터|~|–|—|to)\\s*(\\d{4}-\\d{2}-\\d{2})"
    );

    public CalendarAssistantRequestPolicy() {
    }

    public String localResponseIfRequired(String message, int maximumQueryDays) {
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (isGreeting(normalized)) {
            return "안녕하세요. 일정 조회와 빈 시간 찾기를 도와드릴 수 있어요.";
        }
        if (containsUnsupportedMutation(normalized)) {
            return "현재는 일정 조회와 빈 시간 찾기만 지원합니다. 일정이나 작업의 생성·수정·삭제는 도와드릴 수 없어요.";
        }
        if (!isCalendarQuestion(normalized)) {
            return "저는 일정 조회와 빈 시간 찾기를 돕는 캘린더 도우미예요. 궁금한 일정이나 가능한 시간을 물어봐 주세요.";
        }
        if (requestsAvailability(normalized) && hasMissingAvailabilityInput(normalized)) {
            return "빈 시간을 찾으려면 날짜 범위, 하루 중 확인할 시간대, 필요한 최소 시간을 함께 알려주세요.";
        }
        if (exceedsMaximumRange(normalized, maximumQueryDays)) {
            return "한 번에 최대 14일까지만 확인할 수 있어요. 더 짧은 기간으로 나누어 알려주세요.";
        }
        return null;
    }

    private boolean isGreeting(String message) {
        return message.matches("^(안녕|안녕하세요|하이|hello|hi|도움말|help)[!. ]*$");
    }

    private boolean containsUnsupportedMutation(String message) {
        return containsAny(
                message,
                "만들",
                "생성",
                "등록",
                "추가",
                "수정",
                "변경",
                "삭제",
                "지워",
                "task",
                "작업",
                "할 일",
                "할일"
        );
    }

    private boolean isCalendarQuestion(String message) {
        return containsAny(message, "일정", "캘린더", "회의", "약속", "스케줄", "시간", "비어", "빈 시간", "가능");
    }

    private boolean requestsAvailability(String message) {
        return containsAny(message, "빈 시간", "비는 시간", "가능한 시간", "시간 있어", "free time");
    }

    private boolean hasMissingAvailabilityInput(String message) {
        return !hasDateRange(message) || !hasTimeWindow(message) || !hasMinimumDuration(message);
    }

    private boolean hasDateRange(String message) {
        return containsAny(message, "오늘", "내일", "이번 주", "다음 주", "주말")
                || ISO_DATE_RANGE.matcher(message).find()
                || message.matches(".*\\d{1,2}월\\s*\\d{1,2}일.*");
    }

    private boolean hasTimeWindow(String message) {
        return containsAny(message, "오전", "오후", "저녁", "업무 시간", "시 이후", "시부터", "시까지");
    }

    private boolean hasMinimumDuration(String message) {
        return message.matches(".*\\d+\\s*(시간|분).*");
    }

    private boolean exceedsMaximumRange(String message, int maximumQueryDays) {
        Matcher matcher = ISO_DATE_RANGE.matcher(message);
        if (!matcher.find()) {
            return false;
        }
        LocalDate startDate = LocalDate.parse(matcher.group(1));
        LocalDate endDate = LocalDate.parse(matcher.group(2));
        return ChronoUnit.DAYS.between(startDate, endDate) + 1 > maximumQueryDays;
    }

    private boolean containsAny(String message, String... phrases) {
        for (String phrase : phrases) {
            if (message.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
