package com.calio.calendar.notification.service;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.client.ApnsMessage;
import com.calio.calendar.notification.client.ApnsSendResult;
import com.calio.calendar.notification.client.ApnsSendResultType;
import com.calio.calendar.notification.client.ApnsClient;
import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.domain.NotificationDelivery;
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

    private final NotificationDeliveryQueryService deliveryQueryService;
    private final NotificationDeliveryCommandService deliveryCommandService;
    private final AccountQueryService accountQueryService;
    private final IosPushDeviceService pushDeviceService;
    private final ApnsClient apnsClient;
    private final ObjectMapper objectMapper;

    public CalendarNotificationDispatcher(
            NotificationDeliveryQueryService deliveryQueryService,
            NotificationDeliveryCommandService deliveryCommandService,
            AccountQueryService accountQueryService,
            IosPushDeviceService pushDeviceService,
            ApnsClient apnsClient,
            ObjectMapper objectMapper
    ) {
        this.deliveryQueryService = deliveryQueryService;
        this.deliveryCommandService = deliveryCommandService;
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

        NotificationDelivery delivery = createClaim(
                accountId, notificationType, scheduleKey, scheduledAt, targetDate, title, groupName
        );
        if (delivery == null) {
            return;
        }

        pushDeviceService.listEligiblePushDevices(accountId)
                .forEach(pushDevice -> sendToPushDevice(delivery, pushDevice));
        delivery.complete("DISPATCHED");
    }

    private boolean isAlreadyClaimed(Long accountId, String type, String key, Instant scheduledAt) {
        return deliveryQueryService.hasDeliveryClaim(accountId, type, key, scheduledAt);
    }

    private NotificationDelivery createClaim(
            Long accountId,
            String type,
            String key,
            Instant scheduledAt,
            LocalDate targetDate,
            String title,
            String groupName
    ) {
        try {
            return deliveryCommandService.create(
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

    private void sendToPushDevice(NotificationDelivery delivery, IosPushDevice pushDevice) {
        ApnsSendResult result = apnsClient.send(new ApnsMessage(
                pushDevice.getApnsToken(),
                payload(delivery),
                delivery.getScheduledAt().plus(Duration.ofMinutes(5))
        ));
        logFailedDelivery(delivery, pushDevice, result);
        if (result.type() == ApnsSendResultType.INVALID_ENDPOINT) {
            pushDeviceService.deactivateInvalidPushDevice(pushDevice);
        }
    }

    private void logFailedDelivery(
            NotificationDelivery delivery,
            IosPushDevice pushDevice,
            ApnsSendResult result
    ) {
        if (result.type() == ApnsSendResultType.ACCEPTED) {
            return;
        }

        log.warn(
                "APNs notification delivery failed. notificationDeliveryId={} iosPushDeviceId={} resultType={} providerRequestId={} reason={}",
                delivery.getId(),
                pushDevice.getId(),
                result.type(),
                result.requestId(),
                result.reason()
        );
    }

    private String payload(NotificationDelivery delivery) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "aps", Map.of("alert", alert(delivery), "sound", "default"),
                    "calio", metadata(delivery)
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize APNs payload.", exception);
        }
    }

    private Map<String, String> alert(NotificationDelivery delivery) {
        Map<String, String> alert = new LinkedHashMap<>();
        alert.put("title", "Calio");
        alert.put("body", visibleBody(delivery));
        return alert;
    }

    private Map<String, String> metadata(NotificationDelivery delivery) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("notificationType", delivery.getNotificationType());
        metadata.put("targetDate", delivery.getTargetDate().toString());
        return metadata;
    }

    private String visibleBody(NotificationDelivery delivery) {
        if ("BRIEFING".equals(delivery.getNotificationType())) {
            return "오늘 일정이 " + delivery.getTitle() + "개 있어요";
        }
        if (delivery.getGroupName() == null) {
            return delivery.getTitle();
        }
        return delivery.getTitle() + " · " + delivery.getGroupName();
    }
}
