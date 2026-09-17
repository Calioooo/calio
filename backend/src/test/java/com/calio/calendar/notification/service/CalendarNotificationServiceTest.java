package com.calio.calendar.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.groupcalendar.service.GroupCalendarService;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.notification.client.ApnsClient;
import com.calio.calendar.notification.client.ApnsSendResult;
import com.calio.calendar.notification.client.ApnsSendResultType;
import com.calio.calendar.notification.domain.CalendarNotificationContent;
import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.NotificationDispatch;
import com.calio.calendar.notification.domain.NotificationScheduleKey;
import com.calio.calendar.notification.repository.NotificationDispatchRepository;
import com.calio.calendar.notification.service.dto.IosPushDeviceTarget;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CalendarNotificationServiceTest {

  @Mock private AccountRepository accountRepository;

  @Mock private EventService eventService;

  @Mock private GroupCalendarService groupCalendarService;

  @Mock private GroupMembershipQueryService groupMembershipQueryService;

  @Mock private NotificationDispatchRepository dispatchRepository;

  @Mock private IosPushDeviceService pushDeviceService;

  @Mock private ApnsClient apnsClient;

  private CalendarNotificationService calendarNotificationService;

  @BeforeEach
  void setUp() {
    calendarNotificationService =
        new CalendarNotificationService(
            accountRepository,
            eventService,
            groupCalendarService,
            groupMembershipQueryService,
            dispatchRepository,
            pushDeviceService,
            apnsClient,
            new ObjectMapper());
  }

  @Test
  @DisplayName("하나의 리마인더 claim은 권한이 있는 모든 iOS 기기에 각각 발송한다")
  void givenTwoEligibleDevices_whenDispatch_thenSendsToEachDevice() {
    // given
    Instant scheduledAt = Instant.parse("2026-09-08T00:00:00Z");
    NotificationDispatch dispatch = dispatch(scheduledAt);
    when(dispatchRepository.saveAndFlush(any(NotificationDispatch.class))).thenReturn(dispatch);
    when(pushDeviceService.listEligiblePushDevices(1L))
        .thenReturn(
            List.of(
                new IosPushDeviceTarget(10L, "iphone-token"),
                new IosPushDeviceTarget(20L, "ipad-token")));
    when(apnsClient.send(any()))
        .thenReturn(new ApnsSendResult(ApnsSendResultType.ACCEPTED, "apns-id", null));

    // when
    calendarNotificationService.dispatch(
        1L,
        NotificationScheduleKey.personalEvent(1L),
        scheduledAt,
        CalendarNotificationContent.schedule(
            CalendarNotificationType.REMINDER, LocalDate.of(2026, 9, 8), "회의", null));

    // then
    verify(apnsClient, times(2)).send(any());
  }

  @Test
  @DisplayName("이미 claim된 리마인더는 APNs에 다시 발송하지 않는다")
  void givenExistingDispatchClaim_whenDispatch_thenSkipsApnsSend() {
    // given
    Instant scheduledAt = Instant.parse("2026-09-08T00:00:00Z");
    when(dispatchRepository.existsByAccountIdAndNotificationTypeAndScheduleKeyAndScheduledAt(
            1L, CalendarNotificationType.REMINDER, "personal:1", scheduledAt))
        .thenReturn(true);

    // when
    calendarNotificationService.dispatch(
        1L,
        NotificationScheduleKey.personalEvent(1L),
        scheduledAt,
        CalendarNotificationContent.schedule(
            CalendarNotificationType.REMINDER, LocalDate.of(2026, 9, 8), "회의", null));

    // then
    verify(dispatchRepository, never()).saveAndFlush(any());
    verify(apnsClient, never()).send(any());
  }

  private NotificationDispatch dispatch(Instant scheduledAt) {
    return new NotificationDispatch(
        1L,
        CalendarNotificationType.REMINDER,
        NotificationScheduleKey.personalEvent(1L),
        scheduledAt);
  }
}
