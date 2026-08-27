package com.calio.calendar.integration.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarIntegrationCommandServiceTest {
    @Mock private GoogleCalendarIntegrationRepository integrationRepository;
    @InjectMocks private GoogleCalendarIntegrationCommandService commandService;

    @Test
    @DisplayName("Account Integration은 Account ID만으로 생성한다")
    void givenAccount_whenCreateIntegration_thenPersistsAccountAggregate() {
        GoogleCalendarIntegration integration = new GoogleCalendarIntegration(1L);
        when(integrationRepository.saveAndFlush(ArgumentMatchers.any())).thenReturn(integration);

        GoogleCalendarIntegration saved = commandService.createIntegration(1L);

        assertThat(saved).isSameAs(integration);
        verify(integrationRepository).saveAndFlush(ArgumentMatchers.any(GoogleCalendarIntegration.class));
    }

    @Test
    @DisplayName("Integration lock 조회는 Account별 aggregate를 반환한다")
    void givenExistingIntegration_whenFindForUpdate_thenReturnsIt() {
        GoogleCalendarIntegration integration = new GoogleCalendarIntegration(1L);
        when(integrationRepository.findByAccountIdForUpdate(1L)).thenReturn(Optional.of(integration));

        assertThat(commandService.findIntegrationForUpdate(1L)).containsSame(integration);
    }
}
