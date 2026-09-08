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
import com.calio.calendar.notification.domain.IosNotificationEndpoint;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IosNotificationEndpointServiceTest {

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private IosNotificationEndpoint previousEndpoint;

    @Mock
    private IosNotificationEndpointQueryService endpointQueryService;

    @Mock
    private IosNotificationEndpointCommandService endpointCommandService;

    private IosNotificationEndpointService endpointService;

    @BeforeEach
    void setUp() {
        endpointService = new IosNotificationEndpointService(
                accountQueryService,
                new ApnsProperties("development", "team", "key", "bundle", "private-key"),
                endpointQueryService,
                endpointCommandService
        );
    }

    @Test
    @DisplayName("다른 설치가 가진 토큰을 등록하면 이전 endpoint를 비활성화하고 토큰을 비운다")
    void givenTokenOwnedByAnotherInstallation_whenRegister_thenRetiresPreviousToken() {
        // given
        when(endpointQueryService.getEndpointWithTokenIfExists("token")).thenReturn(Optional.of(previousEndpoint));
        when(endpointQueryService.getEndpointIfExists(1L, "installation"))
                .thenReturn(Optional.empty());
        when(accountQueryService.getAccount(1L)).thenReturn(new Account());

        // when
        endpointService.register(
                1L,
                "installation",
                "token",
                IosNotificationAuthorizationStatus.AUTHORIZED
        );

        // then
        verify(endpointCommandService).deactivateAndReleaseToken(eq(previousEndpoint), any());
        verify(endpointCommandService).create(any(IosNotificationEndpoint.class));
    }

    @Test
    @DisplayName("동시에 등록된 APNs token이 충돌하면 명시적인 conflict 오류를 반환한다")
    void givenConcurrentTokenRegistration_whenRegister_thenThrowsTokenConflict() {
        // given
        when(endpointQueryService.getEndpointWithTokenIfExists("token")).thenReturn(Optional.empty());
        when(endpointQueryService.getEndpointIfExists(1L, "installation")).thenReturn(Optional.empty());
        when(accountQueryService.getAccount(1L)).thenReturn(new Account());
        when(endpointCommandService.create(any(IosNotificationEndpoint.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate token"));

        // when & then
        assertThatThrownBy(() -> endpointService.register(
                1L,
                "installation",
                "token",
                IosNotificationAuthorizationStatus.AUTHORIZED
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_ENDPOINT_TOKEN_CONFLICT)
        );
    }
}
