package com.calio.calendar.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.groupcalendar.service.GroupCalendarService;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.notification.domain.AccountNotificationSettings;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CalendarNotificationEvaluationServiceTest {

    @Mock
    private EventService eventService;

    @Mock
    private GroupCalendarService groupCalendarService;

    @Mock
    private GroupMembershipQueryService groupMembershipQueryService;

    @Mock
    private GoogleCalendarEventMappingQueryService eventMappingQueryService;

    @Mock
    private GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService;

    @Mock
    private CalendarNotificationDispatcher notificationDispatcher;

    private CalendarNotificationEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new CalendarNotificationEvaluationService(
                eventService,
                groupCalendarService,
                groupMembershipQueryService,
                eventMappingQueryService,
                recurrenceMappingQueryService,
                notificationDispatcher
        );
        when(groupMembershipQueryService.listActiveMemberships(1L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("시간 일정 알림의 targetDate는 일정의 IANA timezone 기준 날짜를 사용한다")
    void givenTimedEventInAnotherTimeZone_whenEvaluate_thenUsesScheduleLocalTargetDate() {
        // given
        Instant startAt = Instant.parse("2026-06-01T01:00:00Z");
        EventResponse event = timedEvent(startAt, "America/Los_Angeles");
        when(eventService.listEvents(eq(1L), any(), any())).thenReturn(List.of(event));
        when(eventMappingQueryService.hasExternalEventMapping(10L, 1L)).thenReturn(false);

        // when
        evaluationService.evaluate(settings(0, false), startAt);

        // then
        ArgumentCaptor<LocalDate> targetDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(notificationDispatcher).dispatch(
                eq(1L),
                eq("REMINDER"),
                eq("personal:10"),
                eq(startAt),
                targetDateCaptor.capture(),
                eq("해외 회의"),
                eq(null)
        );
        assertThat(targetDateCaptor.getValue())
                .isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    @DisplayName("Google 연동 일정은 due minute에도 서버 알림을 만들지 않는다")
    void givenGoogleMappedEvent_whenEvaluate_thenSkipsNotification() {
        // given
        Instant startAt = Instant.parse("2026-06-01T01:00:00Z");
        EventResponse event = timedEvent(startAt, "UTC");
        when(eventService.listEvents(eq(1L), any(), any())).thenReturn(List.of(event));
        when(eventMappingQueryService.hasExternalEventMapping(10L, 1L)).thenReturn(true);

        // when
        evaluationService.evaluate(settings(0, false), startAt);

        // then
        verify(notificationDispatcher, never()).dispatch(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("남은 일정이 없는 브리핑 시각에는 브리핑을 발송하지 않는다")
    void givenNoRemainingSchedules_whenBriefingIsDue_thenSkipsNotification() {
        // given
        Instant briefingTime = Instant.parse("2026-06-01T23:00:00Z");
        when(eventService.listEvents(eq(1L), any(), any())).thenReturn(List.of());

        // when
        evaluationService.evaluate(settings(10, true), briefingTime);

        // then
        verify(notificationDispatcher, never()).dispatch(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("브리핑은 Seoul 기준 당일의 남은 일정만 집계한다")
    void givenSchedulesOutsideBriefingDate_whenEvaluate_thenCountsOnlyTargetDateSchedules() {
        // given
        Instant briefingTime = Instant.parse("2026-06-01T23:00:00Z");
        Instant dayStart = Instant.parse("2026-06-01T15:00:00Z");
        Instant dayEnd = Instant.parse("2026-06-02T15:00:00Z");
        EventResponse targetDateEvent = timedEvent(Instant.parse("2026-06-02T01:00:00Z"), "Asia/Seoul");
        EventResponse nextDateEvent = timedEvent(Instant.parse("2026-06-03T01:00:00Z"), "Asia/Seoul");
        when(eventService.listEvents(eq(1L), any(), any())).thenReturn(List.of(targetDateEvent, nextDateEvent));
        when(eventService.listEvents(1L, dayStart, dayEnd)).thenReturn(List.of(targetDateEvent));
        when(eventMappingQueryService.hasExternalEventMapping(10L, 1L)).thenReturn(false);

        // when
        evaluationService.evaluate(settings(10, true), briefingTime);

        // then
        verify(notificationDispatcher).dispatch(
                eq(1L),
                eq("BRIEFING"),
                eq("briefing:2026-06-02"),
                eq(briefingTime),
                eq(LocalDate.of(2026, 6, 2)),
                eq("1"),
                eq(null)
        );
    }

    private AccountNotificationSettings settings(int timedReminderMinutes, boolean briefingEnabled) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", 1L);
        AccountNotificationSettings settings = new AccountNotificationSettings(account);
        settings.update(
                true,
                timedReminderMinutes,
                120,
                LocalTime.of(9, 0),
                briefingEnabled,
                LocalTime.of(8, 0)
        );
        return settings;
    }

    private EventResponse timedEvent(Instant startAt, String timeZone) {
        return new EventResponse(
                10L,
                "해외 회의",
                "설명",
                startAt,
                startAt.plusSeconds(3_600),
                false,
                timeZone,
                false,
                null,
                false,
                null,
                null,
                startAt,
                startAt
        );
    }
}
