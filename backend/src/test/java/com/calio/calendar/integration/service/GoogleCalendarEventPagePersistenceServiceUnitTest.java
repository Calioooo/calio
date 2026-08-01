package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.external.google.dto.GoogleCalendarEventItem;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTime;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarEventPagePersistenceServiceUnitTest {

    private final GoogleCalendarIntegrationRepository integrationRepository = mock(
            GoogleCalendarIntegrationRepository.class
    );
    private final GoogleCalendarEventMappingRepository mappingRepository = mock(
            GoogleCalendarEventMappingRepository.class
    );
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final TagService tagService = mock(TagService.class);
    private final GoogleCalendarEventTimeNormalizer timeNormalizer = mock(
            GoogleCalendarEventTimeNormalizer.class
    );
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository =
            mock(GoogleCalendarRecurrenceEventMappingRepository.class);
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository =
            mock(GoogleCalendarRecurrenceOverrideMappingRepository.class);
    private final RecurrenceEventRepository recurrenceEventRepository =
            mock(RecurrenceEventRepository.class);
    private final RecurrenceEventOverrideRepository overrideRepository =
            mock(RecurrenceEventOverrideRepository.class);
    private GoogleCalendarEventPagePersistenceService service;

    @BeforeEach
    void setUp() {
        service = new GoogleCalendarEventPagePersistenceService(
                integrationRepository,
                mappingRepository,
                eventRepository,
                accountRepository,
                tagService,
                timeNormalizer,
                recurrenceMappingRepository,
                overrideMappingRepository,
                recurrenceEventRepository,
                overrideRepository
        );
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        when(integration.getId()).thenReturn(10L);
        when(integrationRepository.extendSyncLease(10L, "run")).thenReturn(1);
        when(integrationRepository.getReferenceById(10L)).thenReturn(integration);
        when(mappingRepository.findAllByExternalIdentity(any(), any(), any()))
                .thenReturn(List.of());
        when(accountRepository.getReferenceById(1L)).thenReturn(mock(Account.class));
        when(tagService.getTagOrDefault(1L, null)).thenReturn(mock(Tag.class));
    }

    @Test
    @DisplayName("unexpected normalizer failure는 provider-invalid로 변환하지 않는다")
    void givenUnexpectedNormalizerFailure_whenPersistPage_thenPropagatesOriginalFailure() {
        // given
        IllegalStateException failure = new IllegalStateException("application failure");
        GoogleCalendarEventItem item = timedItem();
        when(timeNormalizer.normalizeSchedule(item.start(), item.end(), "UTC"))
                .thenThrow(failure);

        // when, then
        assertThatThrownBy(() -> service.persistPage(10L, 1L, "run", page(item)))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("unexpected repository failure는 provider-invalid로 변환하지 않는다")
    void givenUnexpectedRepositoryFailure_whenPersistPage_thenPropagatesOriginalFailure() {
        // given
        IllegalStateException failure = new IllegalStateException("database failure");
        GoogleCalendarEventItem item = timedItem();
        when(timeNormalizer.normalizeSchedule(item.start(), item.end(), "UTC"))
                .thenReturn(new NormalizedEventSchedule(
                        Instant.parse("2026-07-01T09:00:00Z"),
                        Instant.parse("2026-07-01T10:00:00Z"),
                        false,
                        "UTC"
                ));
        when(eventRepository.save(any())).thenThrow(failure);

        // when, then
        assertThatThrownBy(() -> service.persistPage(10L, 1L, "run", page(item)))
                .isSameAs(failure);
    }

    private GoogleCalendarEventPage page(GoogleCalendarEventItem item) {
        return new GoogleCalendarEventPage(List.of(item), "next-page", null, "UTC");
    }

    private GoogleCalendarEventItem timedItem() {
        return new GoogleCalendarEventItem(
                "external-id",
                "confirmed",
                null,
                null,
                "Title",
                null,
                List.of(),
                null,
                new GoogleCalendarEventTime(null, "2026-07-01T09:00:00Z", "UTC"),
                new GoogleCalendarEventTime(null, "2026-07-01T10:00:00Z", "UTC")
        );
    }
}
