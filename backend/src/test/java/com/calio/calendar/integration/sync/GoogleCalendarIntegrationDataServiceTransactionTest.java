package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.sync.GoogleCalendarSyncMode;
import com.calio.calendar.integration.mapping.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-google-provider-transaction-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GoogleCalendarIntegrationDataServiceTransactionTest {

    @Autowired
    private GoogleCalendarIntegrationDataService integrationDataService;

    @MockitoBean
    private GoogleOperationLeaseService operationLeaseService;

    @MockitoSpyBean
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private GoogleCalendarEventMappingRepository eventMappingRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TagRepository tagRepository;

    @MockitoBean
    private GoogleOperationJobService operationJobPersistenceService;

    @Test
    @DisplayName("FULL SYNC중 nextToken 저장이 실패하면 이전에 가지고온 page의 data 삭제도 rollback한다")
    void givenCursorFinalizationFailure_whenFinalizeFullSync_thenRollsBackCleanup() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        Event event = eventRepository.saveAndFlush(new Event(
                "Imported event",
                null,
                Instant.parse("2026-07-01T09:00:00Z"),
                Instant.parse("2026-07-01T10:00:00Z"),
                false,
                "UTC",
                null,
                tag,
                account
        ));
        GoogleCalendarEventMapping mapping = eventMappingRepository.saveAndFlush(
                new GoogleCalendarEventMapping(
                        integration,
                        event,
                        "unseen-event",
                        null,
                        null
                )
        );
        doReturn(0).when(integrationRepository).updateNextSyncToken(
                integration.getId(),
                "next-token"
        );

        // when, then
        assertThatThrownBy(() -> integrationDataService.completeSyncRun(
                1L,
                account.getId(),
                integration.getId(),
                "full-run",
                GoogleCalendarSyncMode.FULL,
                Set.of(),
                Set.of(),
                Set.of(),
                "next-token"
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT));
        assertThat(eventMappingRepository.findById(mapping.getId())).isPresent();
        assertThat(eventRepository.findById(event.getId())).isPresent();
        assertThat(integrationRepository.findById(integration.getId()))
                .get()
                .satisfies(savedIntegration -> {
                    assertThat(savedIntegration.getNextSyncToken()).isNull();
                });
    }

    private GoogleCalendarIntegration integration(Long accountId) {
        return new GoogleCalendarIntegration(
                accountId,
                "google-subject",
                "user@example.com",
                "encrypted-refresh-token",
                "encrypted-access-token",
                Instant.parse("2026-07-01T01:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z")
        );
    }
}
