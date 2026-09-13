package com.calio.calendar.notification.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.domain.ImportantReminderOffset;
import com.calio.calendar.account.domain.TimedReminderOffset;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.groupcalendar.controller.dto.GroupCalendarItemResponse;
import com.calio.calendar.groupcalendar.service.GroupCalendarService;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.notification.client.ApnsClient;
import com.calio.calendar.notification.client.ApnsMessage;
import com.calio.calendar.notification.client.ApnsSendResult;
import com.calio.calendar.notification.client.ApnsSendResultType;
import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.domain.NotificationDispatch;
import com.calio.calendar.notification.domain.NotificationScheduleKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CalendarNotificationService {

    private static final ZoneId POLICY_ZONE = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(CalendarNotificationService.class);

    private final EventService eventService;
    private final GroupCalendarService groupCalendarService;
    private final GroupMembershipQueryService groupMembershipQueryService;
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService;
    private final NotificationDispatchQueryService dispatchQueryService;
    private final NotificationDispatchCommandService dispatchCommandService;
    private final AccountQueryService accountQueryService;
    private final IosPushDeviceService pushDeviceService;
    private final ApnsClient apnsClient;
    private final ObjectMapper objectMapper;

    public CalendarNotificationService(
            EventService eventService,
            GroupCalendarService groupCalendarService,
            GroupMembershipQueryService groupMembershipQueryService,
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService,
            NotificationDispatchQueryService dispatchQueryService,
            NotificationDispatchCommandService dispatchCommandService,
            AccountQueryService accountQueryService,
            IosPushDeviceService pushDeviceService,
            ApnsClient apnsClient,
            ObjectMapper objectMapper
    ) {
        this.eventService = eventService;
        this.groupCalendarService = groupCalendarService;
        this.groupMembershipQueryService = groupMembershipQueryService;
        this.eventMappingQueryService = eventMappingQueryService;
        this.recurrenceMappingQueryService = recurrenceMappingQueryService;
        this.dispatchQueryService = dispatchQueryService;
        this.dispatchCommandService = dispatchCommandService;
        this.accountQueryService = accountQueryService;
        this.pushDeviceService = pushDeviceService;
        this.apnsClient = apnsClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void dispatchDueNotifications(Account account, Instant now) {
        Long accountId = account.getId();
        AccountNotificationSettings settings = account.getNotificationSettings();
        Instant dueFrom = now.truncatedTo(ChronoUnit.MINUTES).minus(5, ChronoUnit.MINUTES);
        Instant dueTo = now.truncatedTo(ChronoUnit.MINUTES);
        Instant queryFrom = dueFrom.minus(1, ChronoUnit.DAYS);
        Instant queryTo = dueTo.plus(2, ChronoUnit.DAYS);

        eventService.listEvents(accountId, queryFrom, queryTo).stream()
                .filter(event -> !isGoogleMapped(accountId, event))
                .forEach(event -> dispatchPersonalEventReminders(accountId, settings, event, dueFrom, dueTo));

        listActiveMemberships(accountId).forEach(member -> groupCalendarService.listItems(
                        accountId, member.getGroupSpace().getId(), queryFrom, queryTo
                ).forEach(event -> dispatchGroupEventReminders(
                        accountId, settings, member.getGroupSpace().getName(), event, dueFrom, dueTo
                )));

        dispatchBriefingIfDue(accountId, settings, now, dueFrom, dueTo);
    }

    public void dispatch(
            Long accountId,
            CalendarNotificationType notificationType,
            NotificationScheduleKey scheduleKey,
            Instant scheduledAt,
            LocalDate targetDate,
            String title,
            String groupName
    ) {
        if (isAlreadyClaimed(accountId, notificationType, scheduleKey, scheduledAt)) {
            return;
        }

        NotificationDispatch dispatch = createClaim(
                accountId, notificationType, scheduleKey, scheduledAt, targetDate, title, groupName
        );
        if (dispatch == null) {
            return;
        }

        pushDeviceService.listEligiblePushDevices(accountId)
                .forEach(pushDevice -> sendToPushDevice(dispatch, pushDevice));
    }

    private void dispatchPersonalEventReminders(
            Long accountId,
            AccountNotificationSettings settings,
            EventResponse event,
            Instant dueFrom,
            Instant dueTo
    ) {
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
                dueTo
        );
        if (event.allDay() || event.isRecurrenceOccurrence() || !event.importantEvent()) {
            return;
        }
        ImportantReminderOffset reminderOffset = settings.importantReminderOffset();
        if (reminderOffset.isDisabled()) {
            return;
        }
        dispatchTimedReminder(
                accountId,
                CalendarNotificationType.IMPORTANT,
                reminderOffset.minutes(),
                scheduleKey,
                event.startAt(),
                event.timeZone(),
                event.title(),
                null,
                dueFrom,
                dueTo
        );
    }

    private void dispatchGroupEventReminders(
            Long accountId,
            AccountNotificationSettings settings,
            String groupName,
            GroupCalendarItemResponse event,
            Instant dueFrom,
            Instant dueTo
    ) {
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
                dueTo
        );
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
            Instant dueTo
    ) {
        if (allDay) {
            Instant dueAt = startAt.atZone(POLICY_ZONE).toLocalDate()
                    .atTime(settings.allDayReminderTime()).atZone(POLICY_ZONE).toInstant();
            dispatchIfDue(
                    accountId,
                    CalendarNotificationType.ALL_DAY,
                    scheduleKey,
                    dueAt,
                    startAt,
                    null,
                    title,
                    groupName,
                    dueFrom,
                    dueTo
            );
            return;
        }
        TimedReminderOffset reminderOffset = settings.timedReminderOffset();
        if (reminderOffset.isDisabled()) {
            return;
        }
        dispatchTimedReminder(
                accountId,
                CalendarNotificationType.REMINDER,
                reminderOffset.minutes(),
                scheduleKey,
                startAt,
                timeZone,
                title,
                groupName,
                dueFrom,
                dueTo
        );
    }

    private void dispatchTimedReminder(
            Long accountId,
            CalendarNotificationType type,
            int minutes,
            NotificationScheduleKey key,
            Instant startAt,
            String timeZone,
            String title,
            String groupName,
            Instant dueFrom,
            Instant dueTo
    ) {
        dispatchIfDue(
                accountId,
                type,
                key,
                startAt.minus(Duration.ofMinutes(minutes)),
                startAt,
                timeZone,
                title,
                groupName,
                dueFrom,
                dueTo
        );
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
            Instant dueTo
    ) {
        if (dueAt.isBefore(dueFrom) || dueAt.isAfter(dueTo)) {
            return;
        }
        LocalDate targetDate = startAt.atZone(timeZone == null ? POLICY_ZONE : ZoneId.of(timeZone)).toLocalDate();
        dispatch(accountId, type, key, dueAt, targetDate, title, groupName);
    }

    private boolean isGoogleMapped(Long accountId, EventResponse event) {
        if (event.isRecurrenceOccurrence()) {
            return recurrenceMappingQueryService.hasExternalRecurrenceEventMapping(event.recurrenceId(), accountId);
        }
        return eventMappingQueryService.hasExternalEventMapping(event.id(), accountId);
    }

    private void dispatchBriefingIfDue(
            Long accountId,
            AccountNotificationSettings settings,
            Instant now,
            Instant dueFrom,
            Instant dueTo
    ) {
        if (!settings.dailyBriefingEnabled()) {
            return;
        }

        LocalDate targetDate = now.atZone(POLICY_ZONE).toLocalDate();
        Instant dueAt = targetDate.atTime(settings.dailyBriefingTime())
                .atZone(POLICY_ZONE)
                .toInstant();
        if (dueAt.isBefore(dueFrom) || dueAt.isAfter(dueTo)) {
            return;
        }

        Instant dayStart = targetDate.atStartOfDay(POLICY_ZONE).toInstant();
        Instant dayEnd = targetDate.plusDays(1).atStartOfDay(POLICY_ZONE).toInstant();
        long remainingScheduleCount = remainingPersonalScheduleCount(accountId, now, dayStart, dayEnd)
                + remainingGroupScheduleCount(accountId, now, dayStart, dayEnd);
        if (remainingScheduleCount == 0) {
            return;
        }

        dispatch(
                accountId,
                CalendarNotificationType.BRIEFING,
                NotificationScheduleKey.briefing(targetDate),
                dueAt,
                targetDate,
                Long.toString(remainingScheduleCount),
                null
        );
    }

    private long remainingPersonalScheduleCount(
            Long accountId,
            Instant now,
            Instant dayStart,
            Instant dayEnd
    ) {
        return eventService.listEvents(accountId, dayStart, dayEnd).stream()
                .filter(event -> !isGoogleMapped(accountId, event))
                .filter(event -> isRemainingSchedule(event.allDay(), event.endAt(), now))
                .count();
    }

    private long remainingGroupScheduleCount(
            Long accountId,
            Instant now,
            Instant dayStart,
            Instant dayEnd
    ) {
        return listActiveMemberships(accountId).stream()
                .mapToLong(member -> groupCalendarService.listItems(
                        accountId,
                        member.getGroupSpace().getId(),
                        dayStart,
                        dayEnd
                ).stream().filter(event -> isRemainingSchedule(event.allDay(), event.endAt(), now)).count())
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

    private boolean isAlreadyClaimed(
            Long accountId,
            CalendarNotificationType type,
            NotificationScheduleKey key,
            Instant scheduledAt
    ) {
        return dispatchQueryService.hasDispatchClaim(accountId, type, key, scheduledAt);
    }

    private NotificationDispatch createClaim(
            Long accountId,
            CalendarNotificationType type,
            NotificationScheduleKey key,
            Instant scheduledAt,
            LocalDate targetDate,
            String title,
            String groupName
    ) {
        try {
            return dispatchCommandService.create(
                    accountQueryService.getAccount(accountId),
                    type,
                    key,
                    scheduledAt,
                    targetDate,
                    title,
                    groupName
            );
        } catch (DataIntegrityViolationException ignored) {
            return null;
        }
    }

    private void sendToPushDevice(NotificationDispatch dispatch, IosPushDevice pushDevice) {
        ApnsSendResult result = apnsClient.send(new ApnsMessage(
                pushDevice.getApnsToken(),
                payload(dispatch),
                dispatch.getScheduledAt().plus(Duration.ofMinutes(5))
        ));
        logFailedDispatch(dispatch, pushDevice, result);
        if (result.type() == ApnsSendResultType.INVALID_ENDPOINT) {
            pushDeviceService.deactivateInvalidPushDevice(pushDevice);
        }
    }

    private void logFailedDispatch(
            NotificationDispatch dispatch,
            IosPushDevice pushDevice,
            ApnsSendResult result
    ) {
        if (result.type() == ApnsSendResultType.ACCEPTED) {
            return;
        }

        log.warn(
                "APNs notification dispatch failed. notificationDispatchId={} iosPushDeviceId={} resultType={} providerRequestId={} reason={}",
                dispatch.getId(),
                pushDevice.getId(),
                result.type(),
                result.requestId(),
                result.reason()
        );
    }

    private String payload(NotificationDispatch dispatch) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "aps", Map.of("alert", alert(dispatch), "sound", "default"),
                    "calio", metadata(dispatch)
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize APNs payload.", exception);
        }
    }

    private Map<String, String> alert(NotificationDispatch dispatch) {
        Map<String, String> alert = new LinkedHashMap<>();
        alert.put("title", "Calio");
        alert.put("body", visibleBody(dispatch));
        return alert;
    }

    private Map<String, String> metadata(NotificationDispatch dispatch) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("notificationType", dispatch.getNotificationType().name());
        metadata.put("targetDate", dispatch.getTargetDate().toString());
        return metadata;
    }

    private String visibleBody(NotificationDispatch dispatch) {
        if (dispatch.getNotificationType() == CalendarNotificationType.BRIEFING) {
            return "오늘 일정이 " + dispatch.getTitle() + "개 있어요";
        }
        if (dispatch.getGroupName() == null) {
            return dispatch.getTitle();
        }
        return dispatch.getTitle() + " · " + dispatch.getGroupName();
    }
}
