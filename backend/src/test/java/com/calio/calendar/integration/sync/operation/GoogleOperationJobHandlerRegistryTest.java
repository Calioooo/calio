package com.calio.calendar.integration.sync.operation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleOperationJobHandlerRegistryTest {

    @Test
    @DisplayName("concrete Job type에 등록된 handler로 처리한다")
    void givenRegisteredEventHandler_whenExecutingEventJob_thenDelegatesToHandler() {
        GoogleOperationJobHandler handler = mock();
        GoogleCalendarEventJob job = mock();
        doReturn(GoogleCalendarEventJob.class).when(handler).jobType();

        GoogleOperationJobHandlerRegistry registry = new GoogleOperationJobHandlerRegistry(List.of(handler));

        registry.execute(job, "worker");

        verify(handler).execute(job, "worker");
    }

    @Test
    @DisplayName("같은 Job type을 처리하는 handler가 둘이면 애플리케이션 시작을 거부한다")
    void givenDuplicateHandlerTypes_whenCreatingRegistry_thenRejectsConfiguration() {
        GoogleOperationJobHandler first = mock();
        GoogleOperationJobHandler second = mock();
        doReturn(GoogleCalendarEventJob.class).when(first).jobType();
        doReturn(GoogleCalendarEventJob.class).when(second).jobType();

        assertThatThrownBy(() -> new GoogleOperationJobHandlerRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("등록되지 않은 concrete Job type은 명시적으로 실패한다")
    void givenUnregisteredJobType_whenExecuting_thenThrows() {
        GoogleOperationJob job = mock();
        GoogleOperationJobHandlerRegistry registry = new GoogleOperationJobHandlerRegistry(List.of());

        assertThatThrownBy(() -> registry.execute(job, "worker"))
                .isInstanceOf(GoogleOperationJobHandlerNotFoundException.class);
    }
}
