package com.calio.calendar.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.domain.ImportantReminderOffset;
import com.calio.calendar.account.domain.TimedReminderOffset;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceOverrideRepository;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceOccurrenceResolver;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.notification.client.ApnsClient;
import com.calio.calendar.notification.domain.CalendarNotificationContent;
import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.NotificationScheduleKey;
import com.calio.calendar.notification.repository.IosPushDeviceRepository;
import com.calio.calendar.notification.repository.NotificationDispatchRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.PersonalRecurrenceOccurrenceResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
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
class SendDueCalendarNotificationsPolicyTest {

  @Mock private AccountRepository accountRepository;

  @Mock private EventRepository eventRepository;

  @Mock private RecurrenceEventRepository recurrenceEventRepository;

  @Mock private RecurrenceEventOverrideRepository recurrenceOverrideRepository;

  @Mock private PersonalRecurrenceOccurrenceResolver personalRecurrenceOccurrenceResolver;

  @Mock private GroupMemberRepository groupMemberRepository;

  @Mock private GroupCalendarEventRepository groupCalendarEventRepository;

  @Mock private GroupCalendarRecurrenceEventRepository groupCalendarRecurrenceEventRepository;

  @Mock private GroupCalendarRecurrenceOverrideRepository groupCalendarRecurrenceOverrideRepository;

  @Mock private GroupCalendarRecurrenceOccurrenceResolver groupRecurrenceOccurrenceResolver;

  @Mock private NotificationDispatchRepository dispatchRepository;

  @Mock private IosPushDeviceRepository pushDeviceRepository;

  @Mock private ApnsClient apnsClient;

  private SendDueCalendarNotificationsUseCase sendDueCalendarNotificationsUseCase;

  @BeforeEach
  void setUp() {
    sendDueCalendarNotificationsUseCase =
        spy(
            new SendDueCalendarNotificationsUseCase(
                accountRepository,
                eventRepository,
                recurrenceEventRepository,
                recurrenceOverrideRepository,
                personalRecurrenceOccurrenceResolver,
                groupMemberRepository,
                groupCalendarEventRepository,
                groupCalendarRecurrenceEventRepository,
                groupCalendarRecurrenceOverrideRepository,
                groupRecurrenceOccurrenceResolver,
                dispatchRepository,
                pushDeviceRepository,
                apnsClient,
                Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)));
  }

  @Test
  @DisplayName("시간 일정 알림의 targetDate는 일정의 IANA timezone 기준 날짜를 사용한다")
  void
      givenTimedEventInAnotherTimeZone_whenDispatchingDueNotifications_thenUsesScheduleLocalTargetDate() {
    // given
    Instant startAt = Instant.parse("2026-06-01T01:00:00Z");
    Event event = timedEvent(startAt, "America/Los_Angeles");
    when(eventRepository.findNormalEvents(eq(1L), any(), any())).thenReturn(List.of(event));
    stubDispatch();

    // when
    sendDueCalendarNotificationsUseCase.dispatchAccountNotifications(
        account(TimedReminderOffset.AT_START, false), startAt);

    // then
    ArgumentCaptor<CalendarNotificationContent> contentCaptor =
        ArgumentCaptor.forClass(CalendarNotificationContent.class);
    verify(sendDueCalendarNotificationsUseCase)
        .dispatch(
            eq(1L),
            eq(NotificationScheduleKey.personalEvent(10L)),
            eq(startAt),
            contentCaptor.capture());
    assertThat(contentCaptor.getValue().targetDate()).isEqualTo(LocalDate.of(2026, 5, 31));
  }

  @Test
  @DisplayName("남은 일정이 없는 브리핑 시각에는 브리핑을 발송하지 않는다")
  void givenNoRemainingSchedules_whenBriefingIsDue_thenSkipsNotification() {
    // given
    Instant briefingTime = Instant.parse("2026-06-01T23:00:00Z");
    when(eventRepository.findNormalEvents(eq(1L), any(), any())).thenReturn(List.of());

    // when
    sendDueCalendarNotificationsUseCase.dispatchAccountNotifications(
        account(TimedReminderOffset.MINUTES_10, true), briefingTime);

    // then
    verify(sendDueCalendarNotificationsUseCase, org.mockito.Mockito.never())
        .dispatch(any(), any(), any(), any());
  }

  @Test
  @DisplayName("브리핑은 Seoul 기준 당일의 남은 일정만 집계한다")
  void
      givenSchedulesOutsideBriefingDate_whenDispatchingDueNotifications_thenCountsOnlyTargetDateSchedules() {
    // given
    Instant briefingTime = Instant.parse("2026-06-01T23:00:00Z");
    Event targetDateEvent = timedEvent(Instant.parse("2026-06-02T01:00:00Z"), "Asia/Seoul");
    Event nextDateEvent = timedEvent(Instant.parse("2026-06-03T01:00:00Z"), "Asia/Seoul");
    when(eventRepository.findNormalEvents(eq(1L), any(), any()))
        .thenReturn(List.of(targetDateEvent, nextDateEvent));
    stubDispatch();

    // when
    sendDueCalendarNotificationsUseCase.dispatchAccountNotifications(
        account(TimedReminderOffset.MINUTES_10, true), briefingTime);

    // then
    ArgumentCaptor<CalendarNotificationContent> contentCaptor =
        ArgumentCaptor.forClass(CalendarNotificationContent.class);
    verify(sendDueCalendarNotificationsUseCase)
        .dispatch(
            eq(1L),
            eq(NotificationScheduleKey.briefing(LocalDate.of(2026, 6, 2))),
            eq(briefingTime),
            contentCaptor.capture());
    assertThat(contentCaptor.getValue().type()).isEqualTo(CalendarNotificationType.BRIEFING);
    assertThat(contentCaptor.getValue().body()).isEqualTo("오늘 일정이 1개 있어요");
  }

  private Account account(TimedReminderOffset timedReminderOffset, boolean briefingEnabled) {
    Account account = new Account();
    ReflectionTestUtils.setField(account, "id", 1L);
    account.changeNotificationSettings(
        new AccountNotificationSettings(
            true,
            timedReminderOffset,
            ImportantReminderOffset.MINUTES_120,
            LocalTime.of(9, 0),
            briefingEnabled,
            LocalTime.of(8, 0)));
    return account;
  }

  private void stubDispatch() {
    doNothing().when(sendDueCalendarNotificationsUseCase).dispatch(anyLong(), any(), any(), any());
  }

  private Event timedEvent(Instant startAt, String timeZone) {
    Event event =
        new Event(
            "해외 회의", "설명", startAt, startAt.plusSeconds(3_600), false, timeZone, null, null, null);
    ReflectionTestUtils.setField(event, "id", 10L);
    return event;
  }
}
