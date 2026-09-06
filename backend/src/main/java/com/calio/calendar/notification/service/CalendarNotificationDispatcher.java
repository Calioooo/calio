package com.calio.calendar.notification.service;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.apns.ApnsGateway;
import com.calio.calendar.notification.apns.ApnsMessage;
import com.calio.calendar.notification.apns.ApnsSendResult;
import com.calio.calendar.notification.apns.ApnsSendResultType;
import com.calio.calendar.notification.domain.IosNotificationEndpoint;
import com.calio.calendar.notification.domain.NotificationDelivery;
import com.calio.calendar.notification.domain.NotificationEndpointDelivery;
import com.calio.calendar.notification.repository.NotificationDeliveryRepository;
import com.calio.calendar.notification.repository.NotificationEndpointDeliveryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarNotificationDispatcher {

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationEndpointDeliveryRepository endpointDeliveryRepository;
    private final IosNotificationEndpointService endpointService;
    private final ApnsGateway apnsGateway;
    private final ObjectMapper objectMapper;
    private final AccountQueryService accountQueryService;

    public CalendarNotificationDispatcher(
            NotificationDeliveryRepository deliveryRepository,
            NotificationEndpointDeliveryRepository endpointDeliveryRepository,
            IosNotificationEndpointService endpointService,
            ApnsGateway apnsGateway,
            ObjectMapper objectMapper,
            AccountQueryService accountQueryService
    ) {
        this.deliveryRepository = deliveryRepository;
        this.endpointDeliveryRepository = endpointDeliveryRepository;
        this.endpointService = endpointService;
        this.apnsGateway = apnsGateway;
        this.objectMapper = objectMapper;
        this.accountQueryService = accountQueryService;
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

        endpointService.listEligibleEndpoints(accountId)
                .forEach(endpoint -> sendToEndpoint(delivery, endpoint));
        delivery.complete("DISPATCHED");
    }

    private boolean isAlreadyClaimed(Long accountId, String type, String key, Instant scheduledAt) {
        return deliveryRepository.findByAccount_IdAndNotificationTypeAndScheduleKeyAndScheduledAt(
                accountId, type, key, scheduledAt
        ).isPresent();
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
            return deliveryRepository.saveAndFlush(new NotificationDelivery(
                    accountQueryService.getAccount(accountId),
                    type,
                    key,
                    scheduledAt,
                    targetDate,
                    title,
                    groupName
            ));
        } catch (DataIntegrityViolationException ignored) {
            return null;
        }
    }

    private void sendToEndpoint(NotificationDelivery delivery, IosNotificationEndpoint endpoint) {
        ApnsSendResult result = apnsGateway.send(new ApnsMessage(
                endpoint.getApnsToken(),
                payload(delivery),
                delivery.getScheduledAt().plus(Duration.ofMinutes(5))
        ));
        endpointDeliveryRepository.save(new NotificationEndpointDelivery(
                delivery, endpoint, result.type().name(), result.requestId(), result.reason()
        ));
        if (result.type() == ApnsSendResultType.INVALID_ENDPOINT) {
            endpointService.deactivateInvalidEndpoint(endpoint);
        }
    }

    private String payload(NotificationDelivery delivery) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "aps", Map.of("alert", alert(delivery), "sound", "default"),
                    "calio", metadata(delivery)
            ));
        } catch (JsonProcessingException exception) {
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
