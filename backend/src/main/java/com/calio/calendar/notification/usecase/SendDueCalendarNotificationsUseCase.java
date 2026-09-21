package com.calio.calendar.notification.usecase;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOccurrence;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceOverrideRepository;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceOccurrenceResolver;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.notification.client.ApnsClient;
import com.calio.calendar.notification.client.ApnsMessage;
import com.calio.calendar.notification.client.ApnsSendResult;
import com.calio.calendar.notification.client.ApnsSendResultType;
import com.calio.calendar.notification.domain.CalendarNotificationContent;
import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.domain.NotificationDispatch;
import com.calio.calendar.notification.domain.NotificationDispatchState;
import com.calio.calendar.notification.domain.NotificationScheduleKey;
import com.calio.calendar.notification.repository.IosPushDeviceRepository;
import com.calio.calendar.notification.repository.NotificationDispatchRepository;
import com.calio.calendar.recurrence.domain.PersonalRecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.PersonalRecurrenceOccurrenceResolver;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class SendDueCalendarNotificationsUseCase {

  private static final ZoneId POLICY_ZONE = ZoneId.of("Asia/Seoul");
  private static final Duration DISPATCH_LEASE_DURATION = Duration.ofMinutes(1);
  private static final Duration DISPATCH_RETRY_DELAY = Duration.ofMinutes(1);
  private static final Logger log =
      LoggerFactory.getLogger(SendDueCalendarNotificationsUseCase.class);

  private final AccountRepository accountRepository;
  private final EventRepository eventRepository;
  private final RecurrenceEventRepository recurrenceEventRepository;
  private final RecurrenceEventOverrideRepository recurrenceOverrideRepository;
  private final PersonalRecurrenceOccurrenceResolver personalRecurrenceOccurrenceResolver;
  private final GroupMemberRepository groupMemberRepository;
  private final GroupCalendarEventRepository groupCalendarEventRepository;
  private final GroupCalendarRecurrenceEventRepository groupCalendarRecurrenceEventRepository;
  private final GroupCalendarRecurrenceOverrideRepository groupCalendarRecurrenceOverrideRepository;
  private final GroupCalendarRecurrenceOccurrenceResolver groupRecurrenceOccurrenceResolver;
  private final NotificationDispatchRepository dispatchRepository;
  private final IosPushDeviceRepository pushDeviceRepository;
  private final ApnsClient apnsClient;
  private final Clock clock;

  public SendDueCalendarNotificationsUseCase(
      AccountRepository accountRepository,
      EventRepository eventRepository,
      RecurrenceEventRepository recurrenceEventRepository,
      RecurrenceEventOverrideRepository recurrenceOverrideRepository,
      PersonalRecurrenceOccurrenceResolver personalRecurrenceOccurrenceResolver,
      GroupMemberRepository groupMemberRepository,
      GroupCalendarEventRepository groupCalendarEventRepository,
      GroupCalendarRecurrenceEventRepository groupCalendarRecurrenceEventRepository,
      GroupCalendarRecurrenceOverrideRepository groupCalendarRecurrenceOverrideRepository,
      GroupCalendarRecurrenceOccurrenceResolver groupRecurrenceOccurrenceResolver,
      NotificationDispatchRepository dispatchRepository,
      IosPushDeviceRepository pushDeviceRepository,
      ApnsClient apnsClient,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.eventRepository = eventRepository;
    this.recurrenceEventRepository = recurrenceEventRepository;
    this.recurrenceOverrideRepository = recurrenceOverrideRepository;
    this.personalRecurrenceOccurrenceResolver = personalRecurrenceOccurrenceResolver;
    this.groupMemberRepository = groupMemberRepository;
    this.groupCalendarEventRepository = groupCalendarEventRepository;
    this.groupCalendarRecurrenceEventRepository = groupCalendarRecurrenceEventRepository;
    this.groupCalendarRecurrenceOverrideRepository = groupCalendarRecurrenceOverrideRepository;
    this.groupRecurrenceOccurrenceResolver = groupRecurrenceOccurrenceResolver;
    this.dispatchRepository = dispatchRepository;
    this.pushDeviceRepository = pushDeviceRepository;
    this.apnsClient = apnsClient;
    this.clock = clock;
  }

  public void execute(Instant now) {
    accountRepository
        .findByNotificationSettingsCalendarNotificationsEnabledTrue()
        .forEach(account -> dispatchAccountNotifications(account, now));
  }

  void dispatchAccountNotifications(Account account, Instant now) {
    Long accountId = account.getId();
    AccountNotificationSettings settings = account.getNotificationSettings();
    Instant dueTo = now.truncatedTo(ChronoUnit.MINUTES);
    TimeRange dueRange = new TimeRange(dueTo.minus(Duration.ofMinutes(5)), dueTo);
    TimeRange scheduleQueryRange =
        new TimeRange(
            dueRange.from().minus(Duration.ofDays(1)), dueRange.to().plus(Duration.ofDays(2)));
    List<NotificationSchedule> personalSchedules =
        listPersonalSchedules(accountId, scheduleQueryRange.from(), scheduleQueryRange.to());
    List<NotificationSchedule> groupSchedules =
        listGroupSchedules(accountId, scheduleQueryRange.from(), scheduleQueryRange.to());

    personalSchedules.forEach(
        schedule -> dispatchPersonalScheduleNotifications(accountId, settings, schedule, dueRange));
    groupSchedules.forEach(
        schedule -> dispatchGeneralReminder(accountId, settings, schedule, dueRange));
    dispatchBriefingIfDue(accountId, settings, now, dueRange, personalSchedules, groupSchedules);
  }

  void dispatch(
      Long accountId,
      NotificationScheduleKey scheduleKey,
      Instant scheduledAt,
      CalendarNotificationContent content) {
    Optional<DispatchAttempt> claimedAttempt =
        claimDispatch(accountId, content.type(), scheduleKey, scheduledAt);
    if (claimedAttempt.isEmpty()) {
      return;
    }

    DispatchAttempt attempt = claimedAttempt.get();
    try {
      boolean retryRequired = false;
      for (IosPushDevice pushDevice :
          pushDeviceRepository.findByAccountIdAndActiveTrueAndApnsTokenIsNotNull(accountId)) {
        ApnsSendResultType resultType = sendToPushDevice(attempt, content, pushDevice);
        retryRequired = retryRequired || isRetryable(resultType);
      }
      finishDispatchAttempt(attempt, retryRequired);
    } catch (RuntimeException exception) {
      markDispatchRetryable(attempt);
      throw exception;
    }
  }

  private List<NotificationSchedule> listPersonalSchedules(
      Long accountId, Instant from, Instant to) {
    List<NotificationSchedule> schedules = new ArrayList<>();
    eventRepository
        .findNormalEvents(accountId, from, to)
        .forEach(event -> schedules.add(NotificationSchedule.personal(event)));
    recurrenceEventRepository
        .findExpansionCandidatesStartedBefore(accountId, to)
        .forEach(
            recurrenceEvent ->
                addPersonalRecurrenceSchedules(schedules, recurrenceEvent, from, to));
    addMovedInPersonalRecurrenceSchedules(schedules, accountId, from, to);
    return schedules;
  }

  private void addPersonalRecurrenceSchedules(
      List<NotificationSchedule> schedules,
      RecurrenceEvent recurrenceEvent,
      Instant from,
      Instant to) {
    List<RecurrenceOccurrence> occurrences =
        personalRecurrenceOccurrenceResolver.expand(recurrenceEvent, from, to);
    if (occurrences.isEmpty()) {
      return;
    }
    List<RecurrenceEventOverride> overrides =
        recurrenceOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAtIn(
            recurrenceEvent.getId(),
            occurrences.stream().map(RecurrenceOccurrence::originStartAt).toList());
    personalRecurrenceOccurrenceResolver
        .resolve(recurrenceEvent, occurrences, overrides, from, to)
        .forEach(occurrence -> schedules.add(NotificationSchedule.personal(occurrence)));
  }

  private void addMovedInPersonalRecurrenceSchedules(
      List<NotificationSchedule> schedules, Long accountId, Instant from, Instant to) {
    personalRecurrenceOccurrenceResolver
        .resolveMovedIn(
            recurrenceOverrideRepository.findActiveOverlappingOverrides(accountId, from, to),
            from,
            to)
        .forEach(
            occurrence -> {
              if (schedules.stream().noneMatch(schedule -> schedule.matches(occurrence))) {
                schedules.add(NotificationSchedule.personal(occurrence));
              }
            });
  }

  private List<NotificationSchedule> listGroupSchedules(Long accountId, Instant from, Instant to) {
    List<NotificationSchedule> schedules = new ArrayList<>();
    groupMemberRepository
        .findByAccountIdAndStatusOrderByStatusChangedAtDescGroupSpaceIdDesc(
            accountId, GroupMemberStatus.ACTIVE)
        .forEach(member -> addGroupSchedules(schedules, member, from, to));
    return schedules;
  }

  private void addGroupSchedules(
      List<NotificationSchedule> schedules, GroupMember member, Instant from, Instant to) {
    Long groupSpaceId = member.getGroupSpace().getId();
    String groupName = member.getGroupSpace().getName();
    groupCalendarEventRepository
        .findByGroupSpace_IdAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
            groupSpaceId, to, from)
        .forEach(event -> schedules.add(NotificationSchedule.group(event, groupName)));
    groupCalendarRecurrenceEventRepository
        .findByGroupSpaceIdAndFirstOccurrenceStartAtBefore(groupSpaceId, to)
        .forEach(
            recurrenceEvent ->
                addGroupRecurrenceSchedules(schedules, recurrenceEvent, groupName, from, to));
    addMovedInGroupRecurrenceSchedules(schedules, groupSpaceId, groupName, from, to);
  }

  private void addGroupRecurrenceSchedules(
      List<NotificationSchedule> schedules,
      GroupCalendarRecurrenceEvent recurrenceEvent,
      String groupName,
      Instant from,
      Instant to) {
    List<RecurrenceOccurrence> occurrences =
        groupRecurrenceOccurrenceResolver.expand(recurrenceEvent, from, to);
    if (occurrences.isEmpty()) {
      return;
    }
    List<GroupCalendarRecurrenceOverride> overrides =
        groupCalendarRecurrenceOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAtIn(
            recurrenceEvent.getId(),
            occurrences.stream().map(RecurrenceOccurrence::originStartAt).toList());
    groupRecurrenceOccurrenceResolver
        .resolve(recurrenceEvent, occurrences, overrides, from, to)
        .forEach(occurrence -> schedules.add(NotificationSchedule.group(occurrence, groupName)));
  }

  private void addMovedInGroupRecurrenceSchedules(
      List<NotificationSchedule> schedules,
      Long groupSpaceId,
      String groupName,
      Instant from,
      Instant to) {
    groupRecurrenceOccurrenceResolver
        .resolveMovedIn(
            groupCalendarRecurrenceOverrideRepository.listOverlappingOverrides(
                groupSpaceId, from, to),
            from,
            to)
        .forEach(
            occurrence -> {
              if (schedules.stream().noneMatch(schedule -> schedule.matches(occurrence))) {
                schedules.add(NotificationSchedule.group(occurrence, groupName));
              }
            });
  }

  private void dispatchPersonalScheduleNotifications(
      Long accountId,
      AccountNotificationSettings settings,
      NotificationSchedule schedule,
      TimeRange dueRange) {
    dispatchGeneralReminder(accountId, settings, schedule, dueRange);
    if (!schedule.canReceiveImportantReminder()) {
      return;
    }
    settings
        .importantReminderAt(schedule.startAt())
        .ifPresent(
            dueAt ->
                dispatchIfDue(
                    accountId, CalendarNotificationType.IMPORTANT, schedule, dueAt, dueRange));
  }

  private void dispatchGeneralReminder(
      Long accountId,
      AccountNotificationSettings settings,
      NotificationSchedule schedule,
      TimeRange dueRange) {
    if (schedule.allDay()) {
      Instant dueAt =
          settings.allDayReminderAt(
              schedule.startAt().atZone(POLICY_ZONE).toLocalDate(), POLICY_ZONE);
      dispatchIfDue(accountId, CalendarNotificationType.ALL_DAY, schedule, dueAt, dueRange);
      return;
    }
    settings
        .timedReminderAt(schedule.startAt())
        .ifPresent(
            dueAt ->
                dispatchIfDue(
                    accountId, CalendarNotificationType.REMINDER, schedule, dueAt, dueRange));
  }

  private void dispatchIfDue(
      Long accountId,
      CalendarNotificationType type,
      NotificationSchedule schedule,
      Instant dueAt,
      TimeRange dueRange) {
    if (!dueRange.contains(dueAt)) {
      return;
    }
    dispatch(
        accountId,
        schedule.scheduleKey(),
        dueAt,
        CalendarNotificationContent.schedule(
            type, schedule.targetDate(), schedule.title(), schedule.groupName()));
  }

  private void dispatchBriefingIfDue(
      Long accountId,
      AccountNotificationSettings settings,
      Instant now,
      TimeRange dueRange,
      List<NotificationSchedule> personalSchedules,
      List<NotificationSchedule> groupSchedules) {
    LocalDate targetDate = now.atZone(POLICY_ZONE).toLocalDate();
    settings
        .dailyBriefingAt(targetDate, POLICY_ZONE)
        .filter(dueRange::contains)
        .ifPresent(
            dueAt -> {
              long scheduleCount =
                  countRemainingSchedules(personalSchedules, now)
                      + countRemainingSchedules(groupSchedules, now);
              if (scheduleCount > 0) {
                dispatch(
                    accountId,
                    NotificationScheduleKey.briefing(targetDate),
                    dueAt,
                    CalendarNotificationContent.briefing(targetDate, scheduleCount));
              }
            });
  }

  private long countRemainingSchedules(List<NotificationSchedule> schedules, Instant now) {
    return schedules.stream()
        .filter(schedule -> schedule.occursOn(now, POLICY_ZONE))
        .filter(schedule -> schedule.allDay() || schedule.endAt().isAfter(now))
        .count();
  }

  private Optional<DispatchAttempt> claimDispatch(
      Long accountId,
      CalendarNotificationType type,
      NotificationScheduleKey scheduleKey,
      Instant scheduledAt) {
    Instant now = clock.instant();
    String ownerToken = UUID.randomUUID().toString();
    Instant leaseExpiresAt = now.plus(DISPATCH_LEASE_DURATION);
    try {
      NotificationDispatch dispatch =
          dispatchRepository.saveAndFlush(
              NotificationDispatch.claimed(
                  accountId, type, scheduleKey, scheduledAt, ownerToken, leaseExpiresAt));
      return Optional.of(new DispatchAttempt(dispatch.getId(), scheduledAt, ownerToken));
    } catch (DataIntegrityViolationException exception) {
      Optional<NotificationDispatch> existingClaim =
          dispatchRepository.findDispatchClaim(accountId, type, scheduleKey, scheduledAt);
      if (existingClaim.isEmpty()) {
        throw exception;
      }
      NotificationDispatch dispatch = existingClaim.get();
      if (dispatchRepository.tryAcquire(
              dispatch.getId(),
              ownerToken,
              now,
              leaseExpiresAt,
              NotificationDispatchState.PROCESSING,
              NotificationDispatchState.RETRYABLE)
          != 1) {
        return Optional.empty();
      }
      return Optional.of(new DispatchAttempt(dispatch.getId(), scheduledAt, ownerToken));
    }
  }

  private ApnsSendResultType sendToPushDevice(
      DispatchAttempt dispatch, CalendarNotificationContent content, IosPushDevice pushDevice) {
    String token = pushDevice.getApnsToken();
    ApnsSendResult result =
        apnsClient.send(
            new ApnsMessage(
                token,
                "Calio",
                content.body(),
                Map.of(
                    "notificationType", content.type().name(),
                    "targetDate", content.targetDate().toString()),
                dispatch.scheduledAt().plus(Duration.ofMinutes(5))));
    logFailedDispatch(dispatch, pushDevice, result);
    if (result.type() == ApnsSendResultType.INVALID_ENDPOINT) {
      deactivateInvalidPushDevice(pushDevice.getId(), token);
    }
    return result.type();
  }

  private void deactivateInvalidPushDevice(Long pushDeviceId, String token) {
    pushDeviceRepository.deactivateIfTokenMatches(pushDeviceId, token, clock.instant());
  }

  private boolean isRetryable(ApnsSendResultType resultType) {
    return resultType == ApnsSendResultType.TRANSIENT_FAILURE
        || resultType == ApnsSendResultType.CONFIGURATION_FAILURE;
  }

  private void finishDispatchAttempt(DispatchAttempt attempt, boolean retryRequired) {
    if (retryRequired) {
      markDispatchRetryable(attempt);
      return;
    }
    int updated =
        dispatchRepository.markCompleted(
            attempt.id(),
            attempt.ownerToken(),
            NotificationDispatchState.PROCESSING,
            NotificationDispatchState.COMPLETED);
    if (updated != 1) {
      log.warn("Notification dispatch completion ownership was lost. dispatchId={}", attempt.id());
    }
  }

  private void markDispatchRetryable(DispatchAttempt attempt) {
    int updated =
        dispatchRepository.markRetryable(
            attempt.id(),
            attempt.ownerToken(),
            clock.instant().plus(DISPATCH_RETRY_DELAY),
            NotificationDispatchState.PROCESSING,
            NotificationDispatchState.RETRYABLE);
    if (updated != 1) {
      log.warn("Notification dispatch retry ownership was lost. dispatchId={}", attempt.id());
    }
  }

  private void logFailedDispatch(
      DispatchAttempt dispatch, IosPushDevice pushDevice, ApnsSendResult result) {
    if (result.type() == ApnsSendResultType.ACCEPTED) {
      return;
    }
    log.warn(
        "APNs notification dispatch failed. notificationDispatchId={} iosPushDeviceId={} resultType={} providerRequestId={} reason={}",
        dispatch.id(),
        pushDevice.getId(),
        result.type(),
        result.requestId(),
        result.reason());
  }

  private record TimeRange(Instant from, Instant to) {

    private boolean contains(Instant instant) {
      return !instant.isBefore(from) && !instant.isAfter(to);
    }
  }

  private record DispatchAttempt(Long id, Instant scheduledAt, String ownerToken) {}

  private record NotificationSchedule(
      NotificationScheduleKey scheduleKey,
      String title,
      Instant startAt,
      Instant endAt,
      boolean allDay,
      String timeZone,
      String groupName,
      boolean importantReminderSupported) {

    private static NotificationSchedule personal(Event event) {
      return new NotificationSchedule(
          NotificationScheduleKey.personalEvent(event.getId()),
          event.getTitle(),
          event.getStartAt(),
          event.getEndAt(),
          event.isAllDay(),
          event.getTimeZone(),
          null,
          event.importantEvent());
    }

    private static NotificationSchedule personal(PersonalRecurrenceOccurrence occurrence) {
      return new NotificationSchedule(
          NotificationScheduleKey.personalRecurrence(
              occurrence.recurrenceEvent().getId(), occurrence.originStartAt()),
          occurrence.title(),
          occurrence.startAt(),
          occurrence.endAt(),
          occurrence.allDay(),
          occurrence.timeZone(),
          null,
          false);
    }

    private static NotificationSchedule group(GroupCalendarEvent event, String groupName) {
      return new NotificationSchedule(
          NotificationScheduleKey.groupEvent(event.getId()),
          event.getTitle(),
          event.getStartAt(),
          event.getEndAt(),
          event.isAllDay(),
          event.getTimeZone(),
          groupName,
          false);
    }

    private static NotificationSchedule group(
        GroupCalendarRecurrenceOccurrence occurrence, String groupName) {
      return new NotificationSchedule(
          NotificationScheduleKey.groupRecurrence(
              occurrence.recurrenceEvent().getId(), occurrence.originStartAt()),
          occurrence.title(),
          occurrence.startAt(),
          occurrence.endAt(),
          occurrence.allDay(),
          occurrence.timeZone(),
          groupName,
          false);
    }

    private boolean canReceiveImportantReminder() {
      return importantReminderSupported && !allDay;
    }

    private LocalDate targetDate() {
      return startAt.atZone(timeZone == null ? POLICY_ZONE : ZoneId.of(timeZone)).toLocalDate();
    }

    private boolean occursOn(Instant instant, ZoneId zoneId) {
      LocalDate targetDate = instant.atZone(zoneId).toLocalDate();
      Instant dayStart = targetDate.atStartOfDay(zoneId).toInstant();
      Instant dayEnd = targetDate.plusDays(1).atStartOfDay(zoneId).toInstant();
      return startAt.isBefore(dayEnd) && endAt.isAfter(dayStart);
    }

    private boolean matches(PersonalRecurrenceOccurrence occurrence) {
      return scheduleKey.equals(
          NotificationScheduleKey.personalRecurrence(
              occurrence.recurrenceEvent().getId(), occurrence.originStartAt()));
    }

    private boolean matches(GroupCalendarRecurrenceOccurrence occurrence) {
      return scheduleKey.equals(
          NotificationScheduleKey.groupRecurrence(
              occurrence.recurrenceEvent().getId(), occurrence.originStartAt()));
    }
  }
}
