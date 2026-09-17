package com.calio.calendar.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceOverrideRepository;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceOccurrenceResolver;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.notification.client.ApnsClient;
import com.calio.calendar.notification.client.ApnsSendResult;
import com.calio.calendar.notification.client.ApnsSendResultType;
import com.calio.calendar.notification.domain.CalendarNotificationContent;
import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.domain.NotificationDispatch;
import com.calio.calendar.notification.domain.NotificationScheduleKey;
import com.calio.calendar.notification.repository.IosPushDeviceRepository;
import com.calio.calendar.notification.repository.NotificationDispatchRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.PersonalRecurrenceOccurrenceResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SendDueCalendarNotificationsUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

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
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("하나의 리마인더 claim은 권한이 있는 모든 iOS 기기에 각각 발송한다")
  void givenTwoEligibleDevices_whenDispatch_thenSendsToEachDevice() {
    // given
    Instant scheduledAt = Instant.parse("2026-09-08T00:00:00Z");
    NotificationDispatch dispatch = dispatch(scheduledAt);
    when(dispatchRepository.saveAndFlush(any(NotificationDispatch.class))).thenReturn(dispatch);
    when(pushDeviceRepository.findByAccountIdAndActiveTrueAndApnsTokenIsNotNull(1L))
        .thenReturn(List.of(pushDevice(10L, "iphone-token"), pushDevice(20L, "ipad-token")));
    when(apnsClient.send(any()))
        .thenReturn(new ApnsSendResult(ApnsSendResultType.ACCEPTED, "apns-id", null));

    // when
    sendDueCalendarNotificationsUseCase.dispatch(
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
    when(dispatchRepository.saveAndFlush(any(NotificationDispatch.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate claim"));
    when(dispatchRepository.hasDispatchClaim(
            1L,
            CalendarNotificationType.REMINDER,
            NotificationScheduleKey.personalEvent(1L),
            scheduledAt))
        .thenReturn(true);

    // when
    sendDueCalendarNotificationsUseCase.dispatch(
        1L,
        NotificationScheduleKey.personalEvent(1L),
        scheduledAt,
        CalendarNotificationContent.schedule(
            CalendarNotificationType.REMINDER, LocalDate.of(2026, 9, 8), "회의", null));

    // then
    verify(dispatchRepository).saveAndFlush(any());
    verify(apnsClient, never()).send(any());
  }

  @Test
  @DisplayName("APNs가 현재 토큰을 무효로 판정하면 해당 푸시 기기를 비활성화한다")
  void givenInvalidCurrentToken_whenDispatch_thenDeactivatesPushDevice() {
    // given
    Instant scheduledAt = Instant.parse("2026-09-08T00:00:00Z");
    IosPushDevice pushDevice = pushDevice(10L, "invalid-token");
    when(dispatchRepository.saveAndFlush(any(NotificationDispatch.class)))
        .thenReturn(dispatch(scheduledAt));
    when(pushDeviceRepository.findByAccountIdAndActiveTrueAndApnsTokenIsNotNull(1L))
        .thenReturn(List.of(pushDevice));
    when(pushDeviceRepository.findById(10L)).thenReturn(Optional.of(pushDevice));
    when(apnsClient.send(any()))
        .thenReturn(
            new ApnsSendResult(ApnsSendResultType.INVALID_ENDPOINT, "apns-id", "BadDeviceToken"));

    // when
    sendDueCalendarNotificationsUseCase.dispatch(
        1L,
        NotificationScheduleKey.personalEvent(1L),
        scheduledAt,
        CalendarNotificationContent.schedule(
            CalendarNotificationType.REMINDER, LocalDate.of(2026, 9, 8), "회의", null));

    // then
    assertThat(pushDevice.canReceivePushNotifications()).isFalse();
    assertThat(pushDevice.getApnsToken()).isNull();
    verify(pushDeviceRepository).saveAndFlush(pushDevice);
  }

  private NotificationDispatch dispatch(Instant scheduledAt) {
    return new NotificationDispatch(
        1L,
        CalendarNotificationType.REMINDER,
        NotificationScheduleKey.personalEvent(1L),
        scheduledAt);
  }

  private IosPushDevice pushDevice(Long id, String apnsToken) {
    IosPushDevice pushDevice = new IosPushDevice(1L, "installation-" + id, apnsToken);
    ReflectionTestUtils.setField(pushDevice, "id", id);
    return pushDevice;
  }
}
