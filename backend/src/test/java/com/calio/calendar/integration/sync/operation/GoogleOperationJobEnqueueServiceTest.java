package com.calio.calendar.integration.sync.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionCommandService;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationCommandService;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJob;
import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

class GoogleOperationJobEnqueueServiceTest {

    private final GoogleCalendarConnectionCommandService connectionCommandService = mock();
    private final GoogleCalendarIntegrationCommandService integrationCommandService = mock();
    private final GoogleOperationJobCommandService jobCommandService = mock();
    private final GoogleOperationWorker worker = mock();
    private final ObjectMapper objectMapper = mock();
    private final GoogleOperationJobEnqueueService service = new GoogleOperationJobEnqueueService(
            connectionCommandService,
            integrationCommandService,
            jobCommandService,
            worker,
            Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC),
            objectMapper
    );

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Event CREATE Job은 integration과 Event에 고정된 provider identity를 저장한다")
    void givenEventCreate_whenEnqueued_thenStoresDeterministicProviderIdentity() {
        // given
        Event event = mock();
        GoogleCalendarIntegration integration = mock();
        when(event.getId()).thenReturn(40L);
        when(integration.getId()).thenReturn(20L);
        when(integration.allocateGoogleOperationSequence()).thenReturn(1L);
        when(integrationCommandService.tryLockIntegration(10L)).thenReturn(Optional.of(integration));
        when(objectMapper.writeValueAsString(any(GoogleEventJobPayload.class))).thenReturn("payload");
        TransactionSynchronizationManager.initSynchronization();

        // when
        boolean enqueued = service.enqueueEventCreated(10L, event);

        // then
        ArgumentCaptor<GoogleOperationJob> jobCaptor = ArgumentCaptor.forClass(GoogleOperationJob.class);
        verify(jobCommandService).enqueueOperationJob(jobCaptor.capture());
        assertThat(enqueued).isTrue();
        assertThat(((GoogleCalendarEventJob) jobCaptor.getValue()).getProviderIdentity())
                .isEqualTo("c100000000000000140000000000000028");
    }
}
