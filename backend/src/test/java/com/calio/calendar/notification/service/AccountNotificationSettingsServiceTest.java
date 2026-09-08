package com.calio.calendar.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.domain.AccountNotificationSettings;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AccountNotificationSettingsServiceTest {

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private AccountNotificationSettingsQueryService settingsQueryService;

    @Mock
    private AccountNotificationSettingsCommandService settingsCommandService;

    private AccountNotificationSettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsService = new AccountNotificationSettingsService(
                accountQueryService,
                settingsQueryService,
                settingsCommandService
        );
    }

    @Test
    @DisplayName("동시 최초 생성 충돌 후 저장된 알림 설정을 다시 조회한다")
    void givenConcurrentDefaultCreation_whenGet_thenReturnsPersistedSettings() {
        // given
        Account account = new Account();
        AccountNotificationSettings settings = new AccountNotificationSettings(account);
        when(settingsQueryService.getSettingsIfExists(1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(settings));
        when(accountQueryService.getAccount(1L)).thenReturn(account);
        when(settingsCommandService.createDefaultSettings(account))
                .thenThrow(new DataIntegrityViolationException("duplicate account"));

        // when
        AccountNotificationSettings result = settingsService.get(1L);

        // then
        assertThat(result).isSameAs(settings);
        verify(settingsCommandService).createDefaultSettings(account);
    }
}
