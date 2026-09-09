package com.calio.calendar.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.client.ApnsSendResult;
import com.calio.calendar.notification.client.ApnsSendResultType;
import com.calio.calendar.notification.client.ApnsClient;
import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.domain.NotificationDelivery;
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
class CalendarNotificationDispatcherTest {

    @Mock
    private NotificationDeliveryQueryService deliveryQueryService;

    @Mock
    private NotificationDeliveryCommandService deliveryCommandService;

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private IosPushDeviceService pushDeviceService;

    @Mock
    private ApnsClient apnsClient;

    @Mock
    private IosPushDevice iphonePushDevice;

    @Mock
    private IosPushDevice ipadPushDevice;

    private CalendarNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new CalendarNotificationDispatcher(
                deliveryQueryService,
                deliveryCommandService,
                accountQueryService,
                pushDeviceService,
                apnsClient,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("하나의 리마인더 claim은 권한이 있는 모든 iOS 기기에 각각 발송한다")
    void givenTwoEligibleEndpoints_whenDispatch_thenSendsToEachEndpoint() {
        // given
        Instant scheduledAt = Instant.parse("2026-09-08T00:00:00Z");
        NotificationDelivery delivery = delivery(scheduledAt);
        when(deliveryQueryService.hasDeliveryClaim(1L, "REMINDER", "personal:1", scheduledAt))
                .thenReturn(false);
        when(accountQueryService.getAccount(1L)).thenReturn(new Account());
        when(deliveryCommandService.create(
                any(Account.class),
                eq("REMINDER"),
                eq("personal:1"),
                eq(scheduledAt),
                eq(LocalDate.of(2026, 9, 8)),
                eq("회의"),
                eq(null)
        )).thenReturn(delivery);
        when(pushDeviceService.listEligiblePushDevices(1L)).thenReturn(List.of(iphonePushDevice, ipadPushDevice));
        when(iphonePushDevice.getApnsToken()).thenReturn("iphone-token");
        when(ipadPushDevice.getApnsToken()).thenReturn("ipad-token");
        when(apnsClient.send(any())).thenReturn(new ApnsSendResult(
                ApnsSendResultType.ACCEPTED,
                "apns-id",
                null
        ));

        // when
        dispatcher.dispatch(
                1L,
                "REMINDER",
                "personal:1",
                scheduledAt,
                LocalDate.of(2026, 9, 8),
                "회의",
                null
        );

        // then
        verify(apnsClient, times(2)).send(any());
    }

    @Test
    @DisplayName("이미 claim된 리마인더는 APNs에 다시 발송하지 않는다")
    void givenExistingDeliveryClaim_whenDispatch_thenSkipsApnsSend() {
        // given
        Instant scheduledAt = Instant.parse("2026-09-08T00:00:00Z");
        when(deliveryQueryService.hasDeliveryClaim(1L, "REMINDER", "personal:1", scheduledAt))
                .thenReturn(true);

        // when
        dispatcher.dispatch(
                1L,
                "REMINDER",
                "personal:1",
                scheduledAt,
                LocalDate.of(2026, 9, 8),
                "회의",
                null
        );

        // then
        verify(deliveryCommandService, never()).create(any(), any(), any(), any(), any(), any(), any());
        verify(apnsClient, never()).send(any());
    }

    private NotificationDelivery delivery(Instant scheduledAt) {
        return new NotificationDelivery(
                new Account(),
                "REMINDER",
                "personal:1",
                scheduledAt,
                LocalDate.of(2026, 9, 8),
                "회의",
                null
        );
    }
}
