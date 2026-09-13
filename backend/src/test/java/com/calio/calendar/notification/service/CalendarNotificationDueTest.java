package com.calio.calendar.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.domain.ImportantReminderOffset;
import com.calio.calendar.account.domain.TimedReminderOffset;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.groupcalendar.service.GroupCalendarService;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.notification.client.ApnsClient;
import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.NotificationScheduleKey;
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
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CalendarNotificationDueTest {

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
    private NotificationDispatchQueryService dispatchQueryService;

    @Mock
    private NotificationDispatchCommandService dispatchCommandService;

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private IosPushDeviceService pushDeviceService;

    @Mock
    private ApnsClient apnsClient;

    private CalendarNotificationService calendarNotificationService;

    @BeforeEach
    void setUp() {
        calendarNotificationService = spy(new CalendarNotificationService(
                eventService,
                groupCalendarService,
                groupMembershipQueryService,
                eventMappingQueryService,
                recurrenceMappingQueryService,
                dispatchQueryService,
                dispatchCommandService,
                accountQueryService,
                pushDeviceService,
                apnsClient,
                new ObjectMapper()
        ));
        when(groupMembershipQueryService.listActiveMemberships(1L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("시간 일정 알림의 targetDate는 일정의 IANA timezone 기준 날짜를 사용한다")
    void givenTimedEventInAnotherTimeZone_whenDispatchingDueNotifications_thenUsesScheduleLocalTargetDate() {
        // given
        Instant startAt = Instant.parse("2026-06-01T01:00:00Z");
        EventResponse event = timedEvent(startAt, "America/Los_Angeles");
        when(eventService.listEvents(eq(1L), any(), any())).thenReturn(List.of(event));
        when(eventMappingQueryService.hasExternalEventMapping(10L, 1L)).thenReturn(false);
        stubDispatch();

        // when
        calendarNotificationService.dispatchDueNotifications(account(TimedReminderOffset.AT_START, false), startAt);

        // then
        ArgumentCaptor<LocalDate> targetDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(calendarNotificationService).dispatch(
                eq(1L),
                eq(CalendarNotificationType.REMINDER),
                eq(NotificationScheduleKey.personalEvent(10L)),
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
    void givenGoogleMappedEvent_whenDispatchingDueNotifications_thenSkipsNotification() {
        // given
        Instant startAt = Instant.parse("2026-06-01T01:00:00Z");
        EventResponse event = timedEvent(startAt, "UTC");
        when(eventService.listEvents(eq(1L), any(), any())).thenReturn(List.of(event));
        when(eventMappingQueryService.hasExternalEventMapping(10L, 1L)).thenReturn(true);

        // when
        calendarNotificationService.dispatchDueNotifications(account(TimedReminderOffset.AT_START, false), startAt);

        // then
        verify(calendarNotificationService, never()).dispatch(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("남은 일정이 없는 브리핑 시각에는 브리핑을 발송하지 않는다")
    void givenNoRemainingSchedules_whenBriefingIsDue_thenSkipsNotification() {
        // given
        Instant briefingTime = Instant.parse("2026-06-01T23:00:00Z");
        when(eventService.listEvents(eq(1L), any(), any())).thenReturn(List.of());

        // when
        calendarNotificationService.dispatchDueNotifications(account(TimedReminderOffset.MINUTES_10, true), briefingTime);

        // then
        verify(calendarNotificationService, never()).dispatch(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("브리핑은 Seoul 기준 당일의 남은 일정만 집계한다")
    void givenSchedulesOutsideBriefingDate_whenDispatchingDueNotifications_thenCountsOnlyTargetDateSchedules() {
        // given
        Instant briefingTime = Instant.parse("2026-06-01T23:00:00Z");
        Instant dayStart = Instant.parse("2026-06-01T15:00:00Z");
        Instant dayEnd = Instant.parse("2026-06-02T15:00:00Z");
        EventResponse targetDateEvent = timedEvent(Instant.parse("2026-06-02T01:00:00Z"), "Asia/Seoul");
        EventResponse nextDateEvent = timedEvent(Instant.parse("2026-06-03T01:00:00Z"), "Asia/Seoul");
        when(eventService.listEvents(eq(1L), any(), any())).thenReturn(List.of(targetDateEvent, nextDateEvent));
        when(eventService.listEvents(1L, dayStart, dayEnd)).thenReturn(List.of(targetDateEvent));
        when(eventMappingQueryService.hasExternalEventMapping(10L, 1L)).thenReturn(false);
        stubDispatch();

        // when
        calendarNotificationService.dispatchDueNotifications(account(TimedReminderOffset.MINUTES_10, true), briefingTime);

        // then
        verify(calendarNotificationService).dispatch(
                eq(1L),
                eq(CalendarNotificationType.BRIEFING),
                eq(NotificationScheduleKey.briefing(LocalDate.of(2026, 6, 2))),
                eq(briefingTime),
                eq(LocalDate.of(2026, 6, 2)),
                eq("1"),
                eq(null)
        );
    }

    private Account account(TimedReminderOffset timedReminderOffset, boolean briefingEnabled) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", 1L);
        account.changeNotificationSettings(new AccountNotificationSettings(
                true,
                timedReminderOffset,
                ImportantReminderOffset.MINUTES_120,
                LocalTime.of(9, 0),
                briefingEnabled,
                LocalTime.of(8, 0)
        ));
        return account;
    }

    private void stubDispatch() {
        doNothing().when(calendarNotificationService)
                .dispatch(anyLong(), any(), any(), any(), any(), any(), any());
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
