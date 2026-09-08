package com.calio.calendar.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.notification.apns.ApnsGateway;
import com.calio.calendar.notification.apns.ApnsSendResult;
import com.calio.calendar.notification.apns.ApnsSendResultType;
import com.calio.calendar.notification.domain.IosNotificationEndpoint;
import com.calio.calendar.notification.domain.NotificationDelivery;
import com.calio.calendar.notification.repository.NotificationEndpointDeliveryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarNotificationDispatcherTest {

    @Mock
    private NotificationDeliveryQueryService deliveryQueryService;

    @Mock
    private NotificationEndpointDeliveryRepository endpointDeliveryRepository;

    @Mock
    private IosNotificationEndpointService endpointService;

    @Mock
    private ApnsGateway apnsGateway;

    @Mock
    private NotificationDeliveryClaimService deliveryClaimService;

    @Mock
    private IosNotificationEndpoint iphoneEndpoint;

    @Mock
    private IosNotificationEndpoint ipadEndpoint;

    private CalendarNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new CalendarNotificationDispatcher(
                deliveryQueryService,
                endpointDeliveryRepository,
                endpointService,
                apnsGateway,
                new ObjectMapper(),
                deliveryClaimService
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
        when(deliveryClaimService.claim(
                eq(1L),
                eq("REMINDER"),
                eq("personal:1"),
                eq(scheduledAt),
                eq(LocalDate.of(2026, 9, 8)),
                eq("회의"),
                eq(null)
        )).thenReturn(delivery);
        when(endpointService.listEligibleEndpoints(1L)).thenReturn(List.of(iphoneEndpoint, ipadEndpoint));
        when(iphoneEndpoint.getApnsToken()).thenReturn("iphone-token");
        when(ipadEndpoint.getApnsToken()).thenReturn("ipad-token");
        when(apnsGateway.send(any())).thenReturn(new ApnsSendResult(
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
        verify(apnsGateway, times(2)).send(any());
        verify(endpointDeliveryRepository, times(2)).save(any());
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
        verify(deliveryClaimService, never()).claim(any(), any(), any(), any(), any(), any(), any());
        verify(apnsGateway, never()).send(any());
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
