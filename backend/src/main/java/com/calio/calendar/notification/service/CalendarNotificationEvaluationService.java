package com.calio.calendar.notification.service;

import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.groupcalendar.controller.dto.GroupCalendarItemResponse;
import com.calio.calendar.groupcalendar.service.GroupCalendarService;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.notification.domain.AccountNotificationSettings;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarNotificationEvaluationService {

    private static final ZoneId POLICY_ZONE = ZoneId.of("Asia/Seoul");

    private final EventService eventService;
    private final GroupCalendarService groupCalendarService;
    private final GroupMemberRepository groupMemberRepository;
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService;
    private final CalendarNotificationDispatcher notificationDispatcher;

    public CalendarNotificationEvaluationService(
            EventService eventService,
            GroupCalendarService groupCalendarService,
            GroupMemberRepository groupMemberRepository,
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService,
            CalendarNotificationDispatcher notificationDispatcher
    ) {
        this.eventService = eventService;
        this.groupCalendarService = groupCalendarService;
        this.groupMemberRepository = groupMemberRepository;
        this.eventMappingQueryService = eventMappingQueryService;
        this.recurrenceMappingQueryService = recurrenceMappingQueryService;
        this.notificationDispatcher = notificationDispatcher;
    }

    @Transactional
    public void evaluate(AccountNotificationSettings settings, Instant now) {
        Long accountId = settings.getAccountId();
        Instant dueFrom = now.truncatedTo(ChronoUnit.MINUTES).minus(5, ChronoUnit.MINUTES);
        Instant dueTo = now.truncatedTo(ChronoUnit.MINUTES);
        Instant queryFrom = dueFrom.minus(1, ChronoUnit.DAYS);
        Instant queryTo = dueTo.plus(2, ChronoUnit.DAYS);

        eventService.listEvents(accountId, queryFrom, queryTo).stream()
                .filter(event -> !isGoogleMapped(accountId, event))
                .forEach(event -> evaluatePersonalEvent(accountId, settings, event, dueFrom, dueTo));

        listActiveMemberships(accountId).forEach(member -> groupCalendarService.listItems(
                        accountId, member.getGroupSpace().getId(), queryFrom, queryTo
                ).forEach(event -> evaluateGroupEvent(
                        accountId, settings, member.getGroupSpace().getName(), event, dueFrom, dueTo
                )));

        evaluateBriefing(accountId, settings, now, dueFrom, dueTo, queryFrom, queryTo);
    }

    private void evaluatePersonalEvent(
            Long accountId,
            AccountNotificationSettings settings,
            EventResponse event,
            Instant dueFrom,
            Instant dueTo
    ) {
        String scheduleKey = personalScheduleKey(event);
        dispatchGeneralReminder(accountId, settings, event.startAt(), event.allDay(), scheduleKey, event.title(), null, dueFrom, dueTo);
        if (event.allDay() || event.isRecurrenceOccurrence() || !event.importantEvent()) {
            return;
        }
        dispatchTimedReminder(accountId, "IMPORTANT", settings.getImportantReminderMinutes(), scheduleKey, event.startAt(), event.title(), null, dueFrom, dueTo);
    }

    private void evaluateGroupEvent(
            Long accountId,
            AccountNotificationSettings settings,
            String groupName,
            GroupCalendarItemResponse event,
            Instant dueFrom,
            Instant dueTo
    ) {
        dispatchGeneralReminder(accountId, settings, event.startAt(), event.allDay(), groupScheduleKey(event), event.title(), groupName, dueFrom, dueTo);
    }

    private void dispatchGeneralReminder(
            Long accountId,
            AccountNotificationSettings settings,
            Instant startAt,
            boolean allDay,
            String scheduleKey,
            String title,
            String groupName,
            Instant dueFrom,
            Instant dueTo
    ) {
        if (allDay) {
            Instant dueAt = startAt.atZone(POLICY_ZONE).toLocalDate()
                    .atTime(settings.getAllDayReminderTime()).atZone(POLICY_ZONE).toInstant();
            dispatchIfDue(accountId, "ALL_DAY", scheduleKey, dueAt, startAt, title, groupName, dueFrom, dueTo);
            return;
        }
        dispatchTimedReminder(accountId, "REMINDER", settings.getTimedReminderMinutes(), scheduleKey, startAt, title, groupName, dueFrom, dueTo);
    }

    private void dispatchTimedReminder(Long accountId, String type, Integer minutes, String key, Instant startAt, String title, String groupName, Instant dueFrom, Instant dueTo) {
        if (minutes == null) {
            return;
        }
        dispatchIfDue(accountId, type, key, startAt.minus(Duration.ofMinutes(minutes)), startAt, title, groupName, dueFrom, dueTo);
    }

    private void dispatchIfDue(Long accountId, String type, String key, Instant dueAt, Instant startAt, String title, String groupName, Instant dueFrom, Instant dueTo) {
        if (dueAt.isBefore(dueFrom) || dueAt.isAfter(dueTo)) {
            return;
        }
        LocalDate targetDate = startAt.atZone(POLICY_ZONE).toLocalDate();
        notificationDispatcher.dispatch(accountId, type, key, dueAt, targetDate, title, groupName);
    }

    private boolean isGoogleMapped(Long accountId, EventResponse event) {
        if (event.isRecurrenceOccurrence()) {
            return recurrenceMappingQueryService.hasExternalRecurrenceEventMapping(event.recurrenceId(), accountId);
        }
        return eventMappingQueryService.hasExternalEventMapping(event.id(), accountId);
    }

    private void evaluateBriefing(
            Long accountId,
            AccountNotificationSettings settings,
            Instant now,
            Instant dueFrom,
            Instant dueTo,
            Instant queryFrom,
            Instant queryTo
    ) {
        if (!settings.isDailyBriefingEnabled()) {
            return;
        }

        LocalDate targetDate = now.atZone(POLICY_ZONE).toLocalDate();
        Instant dueAt = targetDate.atTime(settings.getDailyBriefingTime())
                .atZone(POLICY_ZONE)
                .toInstant();
        if (dueAt.isBefore(dueFrom) || dueAt.isAfter(dueTo)) {
            return;
        }

        long remainingScheduleCount = remainingPersonalScheduleCount(
                accountId,
                now,
                queryFrom,
                queryTo
        ) + remainingGroupScheduleCount(accountId, now, queryFrom, queryTo);
        if (remainingScheduleCount == 0) {
            return;
        }

        notificationDispatcher.dispatch(
                accountId,
                "BRIEFING",
                "briefing:" + targetDate,
                dueAt,
                targetDate,
                Long.toString(remainingScheduleCount),
                null
        );
    }

    private long remainingPersonalScheduleCount(
            Long accountId,
            Instant now,
            Instant queryFrom,
            Instant queryTo
    ) {
        return eventService.listEvents(accountId, queryFrom, queryTo).stream()
                .filter(event -> !isGoogleMapped(accountId, event))
                .filter(event -> event.allDay() || event.endAt().isAfter(now))
                .count();
    }

    private long remainingGroupScheduleCount(
            Long accountId,
            Instant now,
            Instant queryFrom,
            Instant queryTo
    ) {
        return listActiveMemberships(accountId).stream()
                .mapToLong(member -> groupCalendarService.listItems(
                        accountId,
                        member.getGroupSpace().getId(),
                        queryFrom,
                        queryTo
                ).stream().filter(event -> event.allDay() || event.endAt().isAfter(now)).count())
                .sum();
    }

    private String personalScheduleKey(EventResponse event) {
        return event.isRecurrenceOccurrence()
                ? "personal-recurrence:" + event.recurrenceId() + ":" + event.originStartAt()
                : "personal:" + event.id();
    }

    private String groupScheduleKey(GroupCalendarItemResponse event) {
        return event.isRecurrenceOccurrence()
                ? "group-recurrence:" + event.recurrenceId() + ":" + event.originStartAt()
                : "group:" + event.id();
    }

    private List<GroupMember> listActiveMemberships(Long accountId) {
        return groupMemberRepository.findByAccountIdAndStatusOrderByStatusChangedAtDescGroupSpaceIdDesc(
                accountId,
                GroupMemberStatus.ACTIVE
        );
    }
}
