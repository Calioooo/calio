package com.calio.calendar.notification.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.groupcalendar.controller.dto.GroupCalendarItemResponse;
import com.calio.calendar.groupcalendar.service.GroupCalendarService;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.notification.client.ApnsClient;
import com.calio.calendar.notification.client.ApnsMessage;
import com.calio.calendar.notification.client.ApnsSendResult;
import com.calio.calendar.notification.client.ApnsSendResultType;
import com.calio.calendar.notification.domain.CalendarNotificationContent;
import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.NotificationDispatch;
import com.calio.calendar.notification.domain.NotificationScheduleKey;
import com.calio.calendar.notification.repository.NotificationDispatchRepository;
import com.calio.calendar.notification.service.dto.IosPushDeviceTarget;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CalendarNotificationService {

  private static final ZoneId POLICY_ZONE = ZoneId.of("Asia/Seoul");
  private static final Logger log = LoggerFactory.getLogger(CalendarNotificationService.class);

  private final AccountRepository accountRepository;
  private final EventService eventService;
  private final GroupCalendarService groupCalendarService;
  private final GroupMembershipQueryService groupMembershipQueryService;
  private final NotificationDispatchRepository dispatchRepository;
  private final IosPushDeviceService pushDeviceService;
  private final ApnsClient apnsClient;
  private final ObjectMapper objectMapper;

  public CalendarNotificationService(
      AccountRepository accountRepository,
      EventService eventService,
      GroupCalendarService groupCalendarService,
      GroupMembershipQueryService groupMembershipQueryService,
      NotificationDispatchRepository dispatchRepository,
      IosPushDeviceService pushDeviceService,
      ApnsClient apnsClient,
      ObjectMapper objectMapper) {
    this.accountRepository = accountRepository;
    this.eventService = eventService;
    this.groupCalendarService = groupCalendarService;
    this.groupMembershipQueryService = groupMembershipQueryService;
    this.dispatchRepository = dispatchRepository;
    this.pushDeviceService = pushDeviceService;
    this.apnsClient = apnsClient;
    this.objectMapper = objectMapper;
  }

  public void dispatchDueNotifications(Instant now) {
    accountRepository
        .findByNotificationSettingsCalendarNotificationsEnabledTrue()
        .forEach(account -> dispatchDueNotifications(account, now));
  }

  void dispatchDueNotifications(Account account, Instant now) {
    Long accountId = account.getId();
    AccountNotificationSettings settings = account.getNotificationSettings();
    Instant dueFrom = now.truncatedTo(ChronoUnit.MINUTES).minus(5, ChronoUnit.MINUTES);
    Instant dueTo = now.truncatedTo(ChronoUnit.MINUTES);
    Instant queryFrom = dueFrom.minus(1, ChronoUnit.DAYS);
    Instant queryTo = dueTo.plus(2, ChronoUnit.DAYS);

    eventService
        .listEvents(accountId, queryFrom, queryTo)
        .forEach(
            event -> dispatchPersonalEventReminders(accountId, settings, event, dueFrom, dueTo));

    listActiveMemberships(accountId)
        .forEach(
            member ->
                groupCalendarService
                    .listItems(accountId, member.getGroupSpace().getId(), queryFrom, queryTo)
                    .forEach(
                        event ->
                            dispatchGroupEventReminders(
                                accountId,
                                settings,
                                member.getGroupSpace().getName(),
                                event,
                                dueFrom,
                                dueTo)));

    dispatchBriefingIfDue(accountId, settings, now, dueFrom, dueTo);
  }

  void dispatch(
      Long accountId,
      NotificationScheduleKey scheduleKey,
      Instant scheduledAt,
      CalendarNotificationContent content) {
    NotificationDispatch dispatch =
        createClaim(accountId, content.type(), scheduleKey, scheduledAt);
    if (dispatch == null) {
      return;
    }

    pushDeviceService
        .listEligiblePushDevices(accountId)
        .forEach(pushDevice -> sendToPushDevice(dispatch, content, pushDevice));
  }

  private void dispatchPersonalEventReminders(
      Long accountId,
      AccountNotificationSettings settings,
      EventResponse event,
      Instant dueFrom,
      Instant dueTo) {
    NotificationScheduleKey scheduleKey = personalScheduleKey(event);
    dispatchGeneralReminder(
        accountId,
        settings,
        event.startAt(),
        event.allDay(),
        event.timeZone(),
        scheduleKey,
        event.title(),
        null,
        dueFrom,
        dueTo);
    if (event.allDay() || event.isRecurrenceOccurrence() || !event.importantEvent()) {
      return;
    }
    settings
        .importantReminderAt(event.startAt())
        .ifPresent(
            dueAt ->
                dispatchIfDue(
                    accountId,
                    CalendarNotificationType.IMPORTANT,
                    scheduleKey,
                    dueAt,
                    event.startAt(),
                    event.timeZone(),
                    event.title(),
                    null,
                    dueFrom,
                    dueTo));
  }

  private void dispatchGroupEventReminders(
      Long accountId,
      AccountNotificationSettings settings,
      String groupName,
      GroupCalendarItemResponse event,
      Instant dueFrom,
      Instant dueTo) {
    dispatchGeneralReminder(
        accountId,
        settings,
        event.startAt(),
        event.allDay(),
        event.timeZone(),
        groupScheduleKey(event),
        event.title(),
        groupName,
        dueFrom,
        dueTo);
  }

  private void dispatchGeneralReminder(
      Long accountId,
      AccountNotificationSettings settings,
      Instant startAt,
      boolean allDay,
      String timeZone,
      NotificationScheduleKey scheduleKey,
      String title,
      String groupName,
      Instant dueFrom,
      Instant dueTo) {
    if (allDay) {
      LocalDate startDate = startAt.atZone(POLICY_ZONE).toLocalDate();
      dispatchIfDue(
          accountId,
          CalendarNotificationType.ALL_DAY,
          scheduleKey,
          settings.allDayReminderAt(startDate, POLICY_ZONE),
          startAt,
          null,
          title,
          groupName,
          dueFrom,
          dueTo);
      return;
    }
    settings
        .timedReminderAt(startAt)
        .ifPresent(
            dueAt ->
                dispatchIfDue(
                    accountId,
                    CalendarNotificationType.REMINDER,
                    scheduleKey,
                    dueAt,
                    startAt,
                    timeZone,
                    title,
                    groupName,
                    dueFrom,
                    dueTo));
  }

  private void dispatchIfDue(
      Long accountId,
      CalendarNotificationType type,
      NotificationScheduleKey key,
      Instant dueAt,
      Instant startAt,
      String timeZone,
      String title,
      String groupName,
      Instant dueFrom,
      Instant dueTo) {
    if (dueAt.isBefore(dueFrom) || dueAt.isAfter(dueTo)) {
      return;
    }
    LocalDate targetDate =
        startAt.atZone(timeZone == null ? POLICY_ZONE : ZoneId.of(timeZone)).toLocalDate();
    dispatch(
        accountId,
        key,
        dueAt,
        CalendarNotificationContent.schedule(type, targetDate, title, groupName));
  }

  private void dispatchBriefingIfDue(
      Long accountId,
      AccountNotificationSettings settings,
      Instant now,
      Instant dueFrom,
      Instant dueTo) {
    LocalDate targetDate = now.atZone(POLICY_ZONE).toLocalDate();
    settings
        .dailyBriefingAt(targetDate, POLICY_ZONE)
        .filter(dueAt -> !dueAt.isBefore(dueFrom) && !dueAt.isAfter(dueTo))
        .ifPresent(dueAt -> dispatchBriefing(accountId, targetDate, dueAt, now));
  }

  private void dispatchBriefing(Long accountId, LocalDate targetDate, Instant dueAt, Instant now) {
    Instant dayStart = targetDate.atStartOfDay(POLICY_ZONE).toInstant();
    Instant dayEnd = targetDate.plusDays(1).atStartOfDay(POLICY_ZONE).toInstant();
    long remainingScheduleCount =
        remainingPersonalScheduleCount(accountId, now, dayStart, dayEnd)
            + remainingGroupScheduleCount(accountId, now, dayStart, dayEnd);
    if (remainingScheduleCount == 0) {
      return;
    }

    dispatch(
        accountId,
        NotificationScheduleKey.briefing(targetDate),
        dueAt,
        CalendarNotificationContent.briefing(targetDate, remainingScheduleCount));
  }

  private long remainingPersonalScheduleCount(
      Long accountId, Instant now, Instant dayStart, Instant dayEnd) {
    return eventService.listEvents(accountId, dayStart, dayEnd).stream()
        .filter(event -> isRemainingSchedule(event.allDay(), event.endAt(), now))
        .count();
  }

  private long remainingGroupScheduleCount(
      Long accountId, Instant now, Instant dayStart, Instant dayEnd) {
    return listActiveMemberships(accountId).stream()
        .mapToLong(
            member ->
                groupCalendarService
                    .listItems(accountId, member.getGroupSpace().getId(), dayStart, dayEnd)
                    .stream()
                    .filter(event -> isRemainingSchedule(event.allDay(), event.endAt(), now))
                    .count())
        .sum();
  }

  private boolean isRemainingSchedule(boolean allDay, Instant endAt, Instant now) {
    return allDay || endAt.isAfter(now);
  }

  private NotificationScheduleKey personalScheduleKey(EventResponse event) {
    return event.isRecurrenceOccurrence()
        ? NotificationScheduleKey.personalRecurrence(event.recurrenceId(), event.originStartAt())
        : NotificationScheduleKey.personalEvent(event.id());
  }

  private NotificationScheduleKey groupScheduleKey(GroupCalendarItemResponse event) {
    return event.isRecurrenceOccurrence()
        ? NotificationScheduleKey.groupRecurrence(event.recurrenceId(), event.originStartAt())
        : NotificationScheduleKey.groupEvent(event.id());
  }

  private List<GroupMember> listActiveMemberships(Long accountId) {
    return groupMembershipQueryService.listActiveMemberships(accountId);
  }

  private NotificationDispatch createClaim(
      Long accountId,
      CalendarNotificationType type,
      NotificationScheduleKey key,
      Instant scheduledAt) {
    if (hasDispatchClaim(accountId, type, key, scheduledAt)) {
      return null;
    }

    try {
      return dispatchRepository.saveAndFlush(
          new NotificationDispatch(accountId, type, key, scheduledAt));
    } catch (DataIntegrityViolationException exception) {
      if (hasDispatchClaim(accountId, type, key, scheduledAt)) {
        return null;
      }
      throw exception;
    }
  }

  private boolean hasDispatchClaim(
      Long accountId,
      CalendarNotificationType type,
      NotificationScheduleKey key,
      Instant scheduledAt) {
    return dispatchRepository.existsByAccountIdAndNotificationTypeAndScheduleKeyAndScheduledAt(
        accountId, type, key.value(), scheduledAt);
  }

  private void sendToPushDevice(
      NotificationDispatch dispatch,
      CalendarNotificationContent content,
      IosPushDeviceTarget pushDevice) {
    ApnsSendResult result =
        apnsClient.send(
            new ApnsMessage(
                pushDevice.apnsToken(),
                payload(content),
                dispatch.getScheduledAt().plus(Duration.ofMinutes(5))));
    logFailedDispatch(dispatch, pushDevice, result);
    if (result.type() == ApnsSendResultType.INVALID_ENDPOINT) {
      pushDeviceService.deactivateInvalidPushDevice(pushDevice.pushDeviceId());
    }
  }

  private void logFailedDispatch(
      NotificationDispatch dispatch, IosPushDeviceTarget pushDevice, ApnsSendResult result) {
    if (result.type() == ApnsSendResultType.ACCEPTED) {
      return;
    }

    log.warn(
        "APNs notification dispatch failed. notificationDispatchId={} iosPushDeviceId={} resultType={} providerRequestId={} reason={}",
        dispatch.getId(),
        pushDevice.pushDeviceId(),
        result.type(),
        result.requestId(),
        result.reason());
  }

  private String payload(CalendarNotificationContent content) {
    try {
      return objectMapper.writeValueAsString(
          Map.of(
              "aps",
                  Map.of(
                      "alert",
                      Map.of("title", "Calio", "body", content.body()),
                      "sound",
                      "default"),
              "calio",
                  Map.of(
                      "notificationType", content.type().name(),
                      "targetDate", content.targetDate().toString())));
    } catch (JacksonException exception) {
      throw new IllegalStateException("Cannot serialize APNs payload.", exception);
    }
  }
}
