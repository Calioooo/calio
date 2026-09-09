package com.calio.calendar.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.notification.client.ApnsProperties;
import com.calio.calendar.notification.domain.IosNotificationAuthorizationStatus;
import com.calio.calendar.notification.domain.IosPushDevice;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IosPushDeviceServiceTest {

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private IosPushDevice previousPushDevice;

    @Mock
    private IosPushDeviceQueryService pushDeviceQueryService;

    @Mock
    private IosPushDeviceCommandService pushDeviceCommandService;

    private IosPushDeviceService pushDeviceService;

    @BeforeEach
    void setUp() {
        pushDeviceService = new IosPushDeviceService(
                accountQueryService,
                new ApnsProperties("development", "team", "key", "bundle", "private-key"),
                pushDeviceQueryService,
                pushDeviceCommandService
        );
    }

    @Test
    @DisplayName("다른 설치가 가진 토큰을 등록하면 이전 endpoint를 비활성화하고 토큰을 비운다")
    void givenTokenOwnedByAnotherInstallation_whenRegister_thenRetiresPreviousToken() {
        // given
        when(pushDeviceQueryService.getPushDeviceWithTokenIfExists("token")).thenReturn(Optional.of(previousPushDevice));
        when(pushDeviceQueryService.getPushDeviceIfExists(1L, "installation"))
                .thenReturn(Optional.empty());
        when(accountQueryService.getAccount(1L)).thenReturn(new Account());

        // when
        pushDeviceService.register(
                1L,
                "installation",
                "token",
                IosNotificationAuthorizationStatus.AUTHORIZED
        );

        // then
        verify(pushDeviceCommandService).deactivateAndReleaseToken(eq(previousPushDevice), any());
        verify(pushDeviceCommandService).create(any(IosPushDevice.class));
    }

    @Test
    @DisplayName("동시에 등록된 APNs token이 충돌하면 명시적인 conflict 오류를 반환한다")
    void givenConcurrentTokenRegistration_whenRegister_thenThrowsTokenConflict() {
        // given
        when(pushDeviceQueryService.getPushDeviceWithTokenIfExists("token")).thenReturn(Optional.empty());
        when(pushDeviceQueryService.getPushDeviceIfExists(1L, "installation")).thenReturn(Optional.empty());
        when(accountQueryService.getAccount(1L)).thenReturn(new Account());
        when(pushDeviceCommandService.create(any(IosPushDevice.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate token"));

        // when & then
        assertThatThrownBy(() -> pushDeviceService.register(
                1L,
                "installation",
                "token",
                IosNotificationAuthorizationStatus.AUTHORIZED
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_ENDPOINT_TOKEN_CONFLICT)
        );
    }
}
