package com.calio.calendar.notification.service;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.client.ApnsMessage;
import com.calio.calendar.notification.client.ApnsSendResult;
import com.calio.calendar.notification.client.ApnsSendResultType;
import com.calio.calendar.notification.client.ApnsClient;
import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.domain.NotificationDispatch;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CalendarNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CalendarNotificationDispatcher.class);

    private final NotificationDispatchQueryService dispatchQueryService;
    private final NotificationDispatchCommandService dispatchCommandService;
    private final AccountQueryService accountQueryService;
    private final IosPushDeviceService pushDeviceService;
    private final ApnsClient apnsClient;
    private final ObjectMapper objectMapper;

    public CalendarNotificationDispatcher(
            NotificationDispatchQueryService dispatchQueryService,
            NotificationDispatchCommandService dispatchCommandService,
            AccountQueryService accountQueryService,
            IosPushDeviceService pushDeviceService,
            ApnsClient apnsClient,
            ObjectMapper objectMapper
    ) {
        this.dispatchQueryService = dispatchQueryService;
        this.dispatchCommandService = dispatchCommandService;
        this.accountQueryService = accountQueryService;
        this.pushDeviceService = pushDeviceService;
        this.apnsClient = apnsClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void dispatch(
            Long accountId,
            String notificationType,
            String scheduleKey,
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
        dispatch.complete("DISPATCHED");
    }

    private boolean isAlreadyClaimed(Long accountId, String type, String key, Instant scheduledAt) {
        return dispatchQueryService.hasDispatchClaim(accountId, type, key, scheduledAt);
    }

    private NotificationDispatch createClaim(
            Long accountId,
            String type,
            String key,
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
        metadata.put("notificationType", dispatch.getNotificationType());
        metadata.put("targetDate", dispatch.getTargetDate().toString());
        return metadata;
    }

    private String visibleBody(NotificationDispatch dispatch) {
        if ("BRIEFING".equals(dispatch.getNotificationType())) {
            return "오늘 일정이 " + dispatch.getTitle() + "개 있어요";
        }
        if (dispatch.getGroupName() == null) {
            return dispatch.getTitle();
        }
        return dispatch.getTitle() + " · " + dispatch.getGroupName();
    }
}
