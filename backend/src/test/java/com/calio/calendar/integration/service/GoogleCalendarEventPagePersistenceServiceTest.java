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
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private GoogleCalendarEventMappingRepository mappingRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TagRepository tagRepository;

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
        pagePersistenceService.persistLastPageAndFinalize(
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
        pagePersistenceService.persistLastPageAndFinalize(
                integration.getId(),
                account.getId(),
                "second-run",
                page(allDayItem("external-1", "Changed"), "cursor-2")
        );

        // then
        Event updatedImport = eventRepository.findById(internalEventId).orElseThrow();
        assertThat(updatedImport.getTitle()).isEqualTo("Changed");
        assertThat(updatedImport.isAllDay()).isTrue();
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
        assertThatThrownBy(() -> pagePersistenceService.persistLastPageAndFinalize(
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
        pagePersistenceService.persistLastPageAndFinalize(
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
        pagePersistenceService.persistLastPageAndFinalize(
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
        pagePersistenceService.persistLastPageAndFinalize(
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
        pagePersistenceService.persistLastPageAndFinalize(
                integration.getId(),
                account.getId(),
                "second-run",
                page(cancelledItem("external-blank"), "cursor-2")
        );
        acquireLease(account.getId(), "third-run");
        pagePersistenceService.persistLastPageAndFinalize(
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

    private GoogleCalendarEventPage page(
            GoogleCalendarEventItem item,
            String nextSyncToken
    ) {
        return new GoogleCalendarEventPage(List.of(item), null, nextSyncToken, "UTC");
    }

    private GoogleCalendarEventItem timedItem(String id, String summary) {
        return new GoogleCalendarEventItem(
                id,
                "confirmed",
                "\"etag-1\"",
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
