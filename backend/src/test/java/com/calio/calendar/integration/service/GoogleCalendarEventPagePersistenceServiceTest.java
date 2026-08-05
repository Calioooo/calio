package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.external.google.dto.GoogleCalendarEventItem;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTime;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.integration.domain.GoogleCalendarItemSnapshot;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.ActiveRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.CancelledRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-google-page-persistence-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GoogleCalendarEventPagePersistenceServiceTest {

    @Autowired
    private GoogleCalendarEventPagePersistenceService pagePersistenceService;

    @Autowired
    private GoogleCalendarPageNormalizer pageNormalizer;

    @Autowired
    private GoogleCalendarProviderDataService providerDataService;

    @Autowired
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private GoogleCalendarEventMappingRepository mappingRepository;

    @Autowired
    private GoogleCalendarRecurrenceEventMappingRepository recurrenceEventMappingRepository;

    @Autowired
    private GoogleCalendarRecurrenceOverrideMappingRepository recurrenceOverrideMappingRepository;

    @Autowired
    private RecurrenceEventRepository recurrenceEventRepository;

    @Autowired
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TagRepository tagRepository;

    @MockitoBean
    private GoogleOperationJobPersistenceService operationJobPersistenceService;

    @MockitoBean
    private GoogleCalendarSyncLeaseService syncLeaseService;

    @Test
    @Transactional
    @DisplayName("동일 external identity 재반영은 내부 Event를 유지하며 provider schedule만 갱신한다")
    void givenRepeatedExternalIdentity_whenPersistPages_thenUpsertsAndPreservesLocalFields() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        Tag fallbackTag = tagRepository.saveAndFlush(new Tag(
                TagType.DEFAULT,
                "기타",
                "#64748B"
        ));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );

        acquireLease(account.getId(), "first-run");
        persistProviderPage(
                integration.getId(),
                account.getId(),
                "first-run",
                page(timedItem("external-1", "Initial"), "cursor-1")
        );
        Event firstImport = eventRepository.findNormalEvents(
                account.getId(),
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-02T00:00:00Z")
        ).getFirst();
        Long internalEventId = firstImport.getId();
        firstImport.changeImportantEvent(true);
        eventRepository.flush();

        // when
        acquireLease(account.getId(), "second-run");
        persistProviderPage(
                integration.getId(),
                account.getId(),
                "second-run",
                page(allDayItem("external-1", "Changed"), "cursor-2")
        );

        // then
        Event updatedImport = eventRepository.findById(internalEventId).orElseThrow();
        assertThat(updatedImport.getTitle()).isEqualTo("Changed");
        assertThat(updatedImport.isAllDay()).isTrue();
        assertThat(updatedImport.getTimeZone()).isNull();
        assertThat(updatedImport.importantEvent()).isTrue();
        assertThat(updatedImport.getTag().getId()).isEqualTo(fallbackTag.getId());
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId()))
                .containsExactly(internalEventId);
        GoogleCalendarIntegration finalized = integrationRepository.findById(integration.getId())
                .orElseThrow();
        assertThat(finalized.getNextSyncToken()).isEqualTo("cursor-2");
        assertThat(finalized.getActiveSyncRunId()).isNull();
        assertThat(finalized.getSyncLeaseExpiresAt()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("normal Event는 page timezone fallback으로 정규화한 schedule과 timezone을 저장한다")
    void givenOffsetlessTimedItemAndPageZone_whenPersistPage_thenStoresCanonicalSchedule() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        tagRepository.saveAndFlush(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        GoogleCalendarEventItem item = new GoogleCalendarEventItem(
                "page-zone-event",
                "confirmed",
                null,
                null,
                "Page zone",
                null,
                List.of(),
                null,
                new GoogleCalendarEventTime(null, "2026-07-01T09:00:00", null),
                new GoogleCalendarEventTime(null, "2026-07-01T10:00:00", null)
        );
        acquireLease(account.getId(), "page-zone-run");

        // when
        persistProviderPage(
                integration.getId(),
                account.getId(),
                "page-zone-run",
                new GoogleCalendarEventPage(
                        List.of(item),
                        null,
                        "cursor-1",
                        "Asia/Seoul"
                )
        );

        // then
        Event imported = eventRepository.findNormalEvents(
                account.getId(),
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T01:00:00Z")
        ).getFirst();
        assertThat(imported.getStartAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(imported.getEndAt()).isEqualTo(Instant.parse("2026-07-01T01:00:00Z"));
        assertThat(imported.isAllDay()).isFalse();
        assertThat(imported.getTimeZone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @Transactional
    @DisplayName("Google이 허용하는 최대 1024자 event id를 손실 없이 저장한다")
    void givenMaximumLengthExternalEventId_whenPersistPage_thenStoresCompleteId() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        tagRepository.saveAndFlush(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        String externalEventId = "a".repeat(1024);
        String googleEtag = "e".repeat(1024);
        acquireLease(account.getId(), "maximum-id-run");

        // when
        persistProviderPage(
                integration.getId(),
                account.getId(),
                "maximum-id-run",
                page(timedItem(externalEventId, "Maximum ID", googleEtag), "cursor-1")
        );

        // then
        assertThat(mappingRepository.findAllWithEventByExternalIdentity(
                integration.getId(),
                GoogleCalendarEventMapping.PRIMARY_CALENDAR_KEY,
                List.of(externalEventId)
        )).singleElement().satisfies(mapping -> {
            assertThat(mapping.getExternalEventId()).isEqualTo(externalEventId);
            assertThat(mapping.getProviderEtag()).isEqualTo(googleEtag);
        });
    }

    @Test
    @Transactional
    @DisplayName("lease를 소유하지 않은 run은 page를 저장할 수 없다")
    void givenDifferentRunId_whenPersistPage_thenReturnsSyncConflict() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        acquireLease(account.getId(), "lease-owner");

        // when, then
        assertThatThrownBy(() -> persistProviderPage(
                integration.getId(),
                account.getId(),
                "different-run",
                new GoogleCalendarEventPage(List.of(), "next-page", null, "UTC")
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    @Transactional
    @DisplayName("마지막 page에 유효한 nextSyncToken이 없으면 sync를 완료할 수 없다")
    void givenMissingNextSyncToken_whenPersistLastPage_thenReturnsTokenMissing(
            String nextSyncToken
    ) {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        acquireLease(account.getId(), "token-run");

        // when, then
        assertThatThrownBy(() -> persistProviderPage(
                integration.getId(),
                account.getId(),
                "token-run",
                new GoogleCalendarEventPage(List.of(), null, nextSyncToken, "UTC")
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_TOKEN_MISSING));
    }

    @Test
    @Transactional
    @DisplayName("Google 일정의 시작 시각이 종료 시각보다 빠르지 않으면 저장하지 않는다")
    void givenInvalidGoogleEventRange_whenPersistPage_thenRejectsResponse() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        tagRepository.saveAndFlush(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        acquireLease(account.getId(), "invalid-range-run");
        GoogleCalendarEventItem item = new GoogleCalendarEventItem(
                "external-invalid-range",
                "confirmed",
                null,
                null,
                "Invalid range",
                null,
                List.of(),
                null,
                new GoogleCalendarEventTime(null, "2026-07-01T10:00:00Z", "UTC"),
                new GoogleCalendarEventTime(null, "2026-07-01T10:00:00Z", "UTC")
        );

        // when, then
        assertThatThrownBy(() -> persistProviderPage(
                integration.getId(),
                account.getId(),
                "invalid-range-run",
                page(item, "cursor-1")
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("동일 page에 external event id가 중복되면 partial 저장 없이 invalid response로 거부한다")
    void givenDuplicateExternalEventIds_whenPersistPage_thenRejectsResponse() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        tagRepository.saveAndFlush(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        acquireLease(account.getId(), "duplicate-run");
        GoogleCalendarEventItem item = timedItem("external-1", "Event");
        GoogleCalendarEventPage page = new GoogleCalendarEventPage(
                List.of(item, item),
                null,
                "cursor-1",
                "UTC"
        );

        // when, then
        assertThatThrownBy(() -> persistProviderPage(
                integration.getId(),
                account.getId(),
                "duplicate-run",
                page
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("recurring으로 바뀐 기존 일반 mapping은 mapping-first로 Event와 함께 제거한다")
    void givenMappedItemBecomesRecurring_whenPersistPage_thenDeletesStaleProviderData() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        tagRepository.saveAndFlush(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        acquireLease(account.getId(), "first-run");
        persistProviderPage(
                integration.getId(),
                account.getId(),
                "first-run",
                page(timedItem("external-1", "Initial"), "cursor-1")
        );

        // when
        acquireLease(account.getId(), "second-run");
        GoogleCalendarEventItem recurringItem = new GoogleCalendarEventItem(
                "external-1",
                "confirmed",
                null,
                null,
                "Recurring",
                null,
                List.of("RRULE:FREQ=DAILY"),
                null,
                timedStart(),
                timedEnd()
        );
        persistProviderPage(
                integration.getId(),
                account.getId(),
                "second-run",
                page(recurringItem, "cursor-2")
        );

        // then
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId())).isEmpty();
        assertThat(eventRepository.findNormalEvents(
                account.getId(),
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-02T00:00:00Z")
        )).isEmpty();
        assertThat(recurrenceEventRepository.count()).isOne();
    }

    @Test
    @Transactional
    @DisplayName("recurrence-event와 active/cancelled override 재처리는 하나의 aggregate로 수렴한다")
    void givenNormalizedRecurrenceReplay_whenPersistPages_thenUpsertsCanonicalAggregate() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        Tag defaultTag = tagRepository.saveAndFlush(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        acquireLease(account.getId(), "recurrence-run");
        NormalizedEventSchedule recurrenceSchedule = new NormalizedEventSchedule(
                Instant.parse("2026-07-01T09:00:00Z"),
                Instant.parse("2026-07-01T10:00:00Z"),
                false,
                "UTC"
        );
        RecurrenceEventUpsert recurrenceEvent = new RecurrenceEventUpsert(
                "recurrence-event-1",
                "recurrence-etag-1",
                Instant.parse("2026-07-01T08:00:00Z"),
                "Daily",
                null,
                recurrenceSchedule,
                List.of("RRULE:FREQ=DAILY")
        );
        ActiveRecurrenceEventOverrideUpsert activeOverride =
                new ActiveRecurrenceEventOverrideUpsert(
                "exception-1",
                "recurrence-event-1",
                Instant.parse("2026-07-02T09:00:00Z"),
                "override-etag-1",
                Instant.parse("2026-07-02T07:00:00Z"),
                "Moved",
                "Final snapshot",
                new NormalizedEventSchedule(
                        Instant.parse("2026-07-02T11:00:00Z"),
                        Instant.parse("2026-07-02T12:00:00Z"),
                        false,
                        "UTC"
                )
        );
        persistPage(
                integration.getId(),
                account.getId(),
                "recurrence-run",
                new GoogleCalendarNormalizedPage(
                        List.of(recurrenceEvent, activeOverride),
                        null,
                        "cursor"
                )
        );

        // when
        CancelledRecurrenceEventOverrideUpsert cancelledOverride =
                new CancelledRecurrenceEventOverrideUpsert(
                "exception-1",
                "recurrence-event-1",
                Instant.parse("2026-07-02T09:00:00Z"),
                "override-etag-2",
                Instant.parse("2026-07-02T08:00:00Z")
        );
        RecurrenceEventUpsert updatedRecurrenceEvent = new RecurrenceEventUpsert(
                "recurrence-event-1",
                "recurrence-etag-2",
                Instant.parse("2026-07-03T08:00:00Z"),
                "Changed",
                "Google description",
                recurrenceSchedule,
                List.of("RRULE:FREQ=WEEKLY")
        );
        persistPage(
                integration.getId(),
                account.getId(),
                "recurrence-run",
                new GoogleCalendarNormalizedPage(
                        List.of(
                                updatedRecurrenceEvent,
                                cancelledOverride
                        ),
                        null,
                        "cursor"
                )
        );

        // then
        assertThat(recurrenceEventRepository.findAll()).singleElement().satisfies(recurrence -> {
            assertThat(recurrence.getRecurrenceTitle()).isEqualTo("Changed");
            assertThat(recurrence.getTag().getId()).isEqualTo(defaultTag.getId());
            assertThat(recurrence.getRecurrenceRules()).containsExactly("RRULE:FREQ=WEEKLY");
        });
        assertThat(recurrenceEventOverrideRepository.findAll()).singleElement().satisfies(override -> {
            assertThat(override.getOriginStartAt())
                    .isEqualTo(Instant.parse("2026-07-02T09:00:00Z"));
            assertThat(override.isDeleted()).isTrue();
        });
        assertThat(recurrenceEventMappingRepository.findAll())
                .singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.getProviderEtag()).isEqualTo("recurrence-etag-2");
                    assertThat(mapping.getProviderUpdatedAt())
                            .isEqualTo(Instant.parse("2026-07-03T08:00:00Z"));
                });
        assertThat(recurrenceOverrideMappingRepository.findAll())
                .singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.getProviderEtag()).isEqualTo("override-etag-2");
                    assertThat(mapping.getProviderUpdatedAt())
                            .isEqualTo(Instant.parse("2026-07-02T08:00:00Z"));
                });
    }

    @Test
    @Transactional
    @DisplayName("동일 override external ID가 다른 recurrence-event를 참조하면 저장하지 않는다")
    void givenOverrideExternalIdMappedToDifferentRecurrenceEvent_whenPersistPage_thenRejectsResponse() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        tagRepository.saveAndFlush(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        acquireLease(account.getId(), "cross-parent-run");
        NormalizedEventSchedule recurrenceSchedule = new NormalizedEventSchedule(
                Instant.parse("2026-07-01T09:00:00Z"),
                Instant.parse("2026-07-01T10:00:00Z"),
                false,
                "UTC"
        );
        RecurrenceEventUpsert firstRecurrenceEvent = new RecurrenceEventUpsert(
                "recurrence-event-a",
                null,
                null,
                "First recurrence event",
                null,
                recurrenceSchedule,
                List.of("RRULE:FREQ=DAILY")
        );
        RecurrenceEventUpsert secondRecurrenceEvent = new RecurrenceEventUpsert(
                "recurrence-event-b",
                null,
                null,
                "Second recurrence event",
                null,
                recurrenceSchedule,
                List.of("RRULE:FREQ=WEEKLY")
        );
        ActiveRecurrenceEventOverrideUpsert firstOverride =
                recurrenceOverride("shared-override", "recurrence-event-a");
        persistPage(
                integration.getId(),
                account.getId(),
                "cross-parent-run",
                new GoogleCalendarNormalizedPage(
                        List.of(firstRecurrenceEvent, secondRecurrenceEvent, firstOverride),
                        null,
                        "cursor"
                )
        );
        ActiveRecurrenceEventOverrideUpsert conflictingOverride =
                recurrenceOverride("shared-override", "recurrence-event-b");

        // when, then
        assertThatThrownBy(() -> persistPage(
                integration.getId(),
                account.getId(),
                "cross-parent-run",
                new GoogleCalendarNormalizedPage(
                        List.of(conflictingOverride),
                        null,
                        "cursor"
                )
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
        assertThat(recurrenceEventOverrideRepository.count()).isOne();
        assertThat(recurrenceOverrideMappingRepository.findAll())
                .singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.getExternalEventId()).isEqualTo("shared-override");
                    assertThat(mapping.getRecurrenceEventMapping().getExternalEventId())
                            .isEqualTo("recurrence-event-a");
                });
    }

    @Test
    @Transactional
    @DisplayName("같은 page에서 override보다 먼저 온 recurrence-event cancellation도 aggregate를 삭제한다")
    void givenCancellationBeforeOverride_whenPersistPage_thenDeletesRecurrenceAggregate() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        tagRepository.saveAndFlush(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        acquireLease(account.getId(), "recurrence-cancellation-run");
        RecurrenceEventUpsert recurrenceEvent = new RecurrenceEventUpsert(
                "recurrence-event-1",
                null,
                null,
                "Daily",
                null,
                new NormalizedEventSchedule(
                        Instant.parse("2026-07-01T09:00:00Z"),
                        Instant.parse("2026-07-01T10:00:00Z"),
                        false,
                        "UTC"
                ),
                List.of("RRULE:FREQ=DAILY")
        );
        ActiveRecurrenceEventOverrideUpsert override =
                new ActiveRecurrenceEventOverrideUpsert(
                        "override-1",
                        "recurrence-event-1",
                        Instant.parse("2026-07-02T09:00:00Z"),
                        null,
                        null,
                        "Moved",
                        null,
                        new NormalizedEventSchedule(
                                Instant.parse("2026-07-02T11:00:00Z"),
                                Instant.parse("2026-07-02T12:00:00Z"),
                                false,
                                "UTC"
                        )
                );
        persistPage(
                integration.getId(),
                account.getId(),
                "recurrence-cancellation-run",
                new GoogleCalendarNormalizedPage(
                        List.of(recurrenceEvent, override),
                        null,
                        "cursor-1"
                )
        );

        // when
        GoogleCalendarNormalizedPage normalizedPage = pageNormalizer.normalize(
                integration.getId(),
                new GoogleCalendarEventPage(
                        List.of(
                                cancelledItem("recurrence-event-1"),
                                deletedRecurrenceOccurrence(
                                        "override-1",
                                        "recurrence-event-1"
                                )
                        ),
                        null,
                        "cursor-2",
                        "UTC"
                ),
                new GoogleCalendarSyncRunContext("access-token")
        );
        persistPage(
                integration.getId(),
                account.getId(),
                "recurrence-cancellation-run",
                normalizedPage
        );

        // then
        assertThat(recurrenceEventMappingRepository.count()).isZero();
        assertThat(recurrenceOverrideMappingRepository.count()).isZero();
        assertThat(recurrenceEventRepository.count()).isZero();
        assertThat(recurrenceEventOverrideRepository.count()).isZero();
    }

    @Test
    @Transactional
    @DisplayName("FULL reconciliation은 unseen provider aggregate를 batch로 삭제하고 sync를 완료한다")
    void givenUnseenProviderData_whenFinalizeFullSync_thenDeletesInBatches() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        Tag defaultTag = tagRepository.saveAndFlush(
                new Tag(TagType.DEFAULT, "기타", "#64748B")
        );
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        acquireLease(account.getId(), "full-reconciliation-run");
        NormalizedEventSchedule eventSchedule = new NormalizedEventSchedule(
                Instant.parse("2026-07-01T09:00:00Z"),
                Instant.parse("2026-07-01T10:00:00Z"),
                false,
                "UTC"
        );
        List<Event> events = new ArrayList<>();
        for (int index = 0; index <= 500; index++) {
            events.add(new Event(
                    "Event " + index,
                    null,
                    eventSchedule.startAt(),
                    eventSchedule.endAt(),
                    false,
                    "UTC",
                    null,
                    defaultTag,
                    account
            ));
        }
        eventRepository.saveAllAndFlush(events);
        List<GoogleCalendarEventMapping> mappings = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            mappings.add(new GoogleCalendarEventMapping(
                    integration,
                    events.get(index),
                    "event-" + index,
                    new GoogleCalendarItemSnapshot(
                            null, null,
                            GoogleCalendarContentHasher.hashEvent(events.get(index)))
            ));
        }
        mappingRepository.saveAllAndFlush(mappings);
        RecurrenceEventUpsert recurrenceEvent = new RecurrenceEventUpsert(
                "recurrence-event-1",
                null,
                null,
                "Daily",
                null,
                eventSchedule,
                List.of("RRULE:FREQ=DAILY")
        );
        ActiveRecurrenceEventOverrideUpsert override =
                new ActiveRecurrenceEventOverrideUpsert(
                        "override-1",
                        "recurrence-event-1",
                        Instant.parse("2026-07-02T09:00:00Z"),
                        null,
                        null,
                        "Moved",
                        null,
                        new NormalizedEventSchedule(
                                Instant.parse("2026-07-02T11:00:00Z"),
                                Instant.parse("2026-07-02T12:00:00Z"),
                                false,
                                "UTC"
                        )
                );
        persistPage(
                integration.getId(),
                account.getId(),
                "full-reconciliation-run",
                new GoogleCalendarNormalizedPage(
                        List.of(
                                recurrenceEvent,
                                override
                        ),
                        null,
                        "next-sync-token"
                )
        );

        // when
        providerDataService.finalizeOwnedReconciliation(
                1L,
                account.getId(),
                integration.getId(),
                "full-reconciliation-run",
                GoogleCalendarSyncMode.FULL,
                Set.of(),
                Set.of(),
                Set.of(),
                "next-sync-token"
        );

        // then
        assertThat(mappingRepository.count()).isZero();
        assertThat(recurrenceEventMappingRepository.count()).isZero();
        assertThat(recurrenceOverrideMappingRepository.count()).isZero();
        assertThat(eventRepository.count()).isZero();
        assertThat(recurrenceEventRepository.count()).isZero();
        assertThat(recurrenceEventOverrideRepository.count()).isZero();
        assertThat(integrationRepository.findById(integration.getId()))
                .get()
                .satisfies(completed -> {
                    assertThat(completed.getNextSyncToken()).isEqualTo("next-sync-token");
                    assertThat(completed.getActiveSyncRunId()).isNull();
                    assertThat(completed.getSyncLeaseExpiresAt()).isNull();
                });
    }

    @Test
    @Transactional
    @DisplayName("blank title의 all-day import는 canonical title을 사용하고 cancelled delta로 hard delete된다")
    void givenBlankAllDayItemThenCancellation_whenPersistPages_thenImportsAndHardDeletesIdempotently() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        tagRepository.saveAndFlush(new Tag(TagType.DEFAULT, "기타", "#64748B"));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        acquireLease(account.getId(), "first-run");
        persistProviderPage(
                integration.getId(),
                account.getId(),
                "first-run",
                page(allDayItem("external-blank", "  "), "cursor-1")
        );
        Event imported = eventRepository.findNormalEvents(
                account.getId(),
                Instant.parse("2026-07-03T00:00:00Z"),
                Instant.parse("2026-07-05T00:00:00Z")
        ).getFirst();
        assertThat(imported.getTitle()).isEqualTo("(제목 없음)");
        assertThat(imported.getDescription()).isEqualTo("Changed description");
        assertThat(imported.isAllDay()).isTrue();

        // when
        acquireLease(account.getId(), "second-run");
        persistProviderPage(
                integration.getId(),
                account.getId(),
                "second-run",
                page(cancelledItem("external-blank"), "cursor-2")
        );
        acquireLease(account.getId(), "third-run");
        persistProviderPage(
                integration.getId(),
                account.getId(),
                "third-run",
                page(cancelledItem("external-blank"), "cursor-3")
        );

        // then
        assertThat(mappingRepository.findEventIdsByIntegrationId(integration.getId())).isEmpty();
        assertThat(eventRepository.findById(imported.getId())).isEmpty();
    }

    private void acquireLease(Long accountId, String runId) {
        assertThat(integrationRepository.acquireSyncLease(accountId, runId)).isOne();
    }

    private void persistProviderPage(
            Long integrationId,
            Long accountId,
            String runId,
            GoogleCalendarEventPage page
    ) {
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext("access-token");
        GoogleCalendarNormalizedPage normalizedPage = pageNormalizer.normalize(
                integrationId,
                page,
                context
        );
        persistPage(
                integrationId,
                accountId,
                runId,
                normalizedPage
        );
        if (page.hasNextPage()) {
            return;
        }
        providerDataService.finalizeOwnedReconciliation(
                1L,
                accountId,
                integrationId,
                runId,
                GoogleCalendarSyncMode.INCREMENTAL,
                context.seenEventIds(),
                context.seenRecurrenceEventIds(),
                context.seenRecurrenceEventOverrideIds(),
                page.nextSyncToken()
        );
    }

    private GoogleCalendarEventPage page(
            GoogleCalendarEventItem item,
            String nextSyncToken
    ) {
        return new GoogleCalendarEventPage(List.of(item), null, nextSyncToken, "UTC");
    }

    private GoogleCalendarEventItem timedItem(String id, String summary) {
        return timedItem(id, summary, "\"etag-1\"");
    }

    private GoogleCalendarEventItem timedItem(
            String id,
            String summary,
            String googleEtag
    ) {
        return new GoogleCalendarEventItem(
                id,
                "confirmed",
                googleEtag,
                Instant.parse("2026-07-01T00:00:00Z"),
                summary,
                "Description",
                List.of(),
                null,
                timedStart(),
                timedEnd()
        );
    }

    private GoogleCalendarEventItem allDayItem(String id, String summary) {
        return new GoogleCalendarEventItem(
                id,
                "confirmed",
                "\"etag-2\"",
                Instant.parse("2026-07-02T00:00:00Z"),
                summary,
                "Changed description",
                List.of(),
                null,
                new GoogleCalendarEventTime("2026-07-03", null, null),
                new GoogleCalendarEventTime("2026-07-05", null, null)
        );
    }

    private GoogleCalendarEventItem cancelledItem(String id) {
        return new GoogleCalendarEventItem(
                id,
                "cancelled",
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null
        );
    }

    private GoogleCalendarEventItem deletedRecurrenceOccurrence(
            String id,
            String recurrenceEventId
    ) {
        return new GoogleCalendarEventItem(
                id,
                "cancelled",
                null,
                Instant.parse("2026-07-02T08:00:00Z"),
                null,
                null,
                List.of(),
                recurrenceEventId,
                new GoogleCalendarEventTime(null, "2026-07-02T09:00:00Z", "UTC"),
                null,
                null
        );
    }

    private ActiveRecurrenceEventOverrideUpsert recurrenceOverride(
            String externalEventId,
            String recurrenceEventExternalId
    ) {
        return new ActiveRecurrenceEventOverrideUpsert(
                externalEventId,
                recurrenceEventExternalId,
                Instant.parse("2026-07-02T09:00:00Z"),
                null,
                null,
                "Moved",
                null,
                new NormalizedEventSchedule(
                        Instant.parse("2026-07-02T11:00:00Z"),
                        Instant.parse("2026-07-02T12:00:00Z"),
                        false,
                        "UTC"
                )
        );
    }

    private GoogleCalendarEventTime timedStart() {
        return new GoogleCalendarEventTime(
                null,
                "2026-07-01T09:00:00Z",
                "UTC"
        );
    }

    private GoogleCalendarEventTime timedEnd() {
        return new GoogleCalendarEventTime(
                null,
                "2026-07-01T10:00:00Z",
                "UTC"
        );
    }

    private void persistPage(
            Long integrationId,
            Long accountId,
            String workerToken,
            GoogleCalendarNormalizedPage page
    ) {
        pagePersistenceService.persistOwnedNormalizedPage(
                1L, integrationId, accountId, workerToken, page
        );
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
