package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.integration.domain.GoogleCalendarSyncTarget;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarMappingSyncStatus;
import com.calio.calendar.integration.domain.GoogleContentHash;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.domain.GoogleOperationJobTrigger;
import com.calio.calendar.integration.domain.GoogleCalendarItemSnapshot;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.service.GoogleOperationJobPersistenceService.GoogleOperationOwnershipLostException;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:google-conflict-persistence-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GoogleCalendarConflictPersistenceIntegrationTest {

    private static final String WORKER_TOKEN = "worker-a";
    private static final NormalizedEventSchedule SCHEDULE = new NormalizedEventSchedule(
            Instant.parse("2026-08-05T00:00:00Z"),
            Instant.parse("2026-08-05T01:00:00Z"),
            false,
            "UTC"
    );

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private GoogleCalendarEventMappingRepository eventMappingRepository;

    @Autowired
    private GoogleOperationJobRepository jobRepository;

    @Autowired
    private GoogleOperationJobPersistenceService jobPersistenceService;

    @Autowired
    private GoogleCalendarSyncLeaseService syncLeaseService;

    @Autowired
    private GoogleCalendarEventPagePersistenceService pagePersistenceService;

    @Test
    @DisplayName("Calio와 Google이 모두 변경되면 매핑과 동기화 작업을 충돌 상태로 저장한다")
    void givenCalioAndGoogleChanged_whenPersistingPage_thenMarksMappingAndJobConflicted() {
        // given
        Fixture fixture = fixture(true);

        // when
        pagePersistenceService.persistOwnedNormalizedPage(
                fixture.jobId(),
                fixture.integrationId(),
                fixture.accountId(),
                WORKER_TOKEN,
                conflictingPage(fixture.externalEventId())
        );

        // then
        assertThat(eventMappingRepository.findById(fixture.mappingId()))
                .get()
                .extracting(GoogleCalendarEventMapping::getSyncStatus)
                .isEqualTo(GoogleCalendarMappingSyncStatus.CONFLICTED);
        assertThat(jobRepository.findById(fixture.jobId()))
                .get()
                .satisfies(job -> assertThat(job.isMappingConflictDetected()).isTrue());
        assertThat(eventRepository.findById(fixture.eventId()))
                .get()
                .extracting(Event::getTitle)
                .isEqualTo("Local title");
    }

    @Test
    @DisplayName("동기화 작업의 소유권 확인이 실패하면 매핑의 충돌 변경도 롤백한다")
    void givenJobOwnershipCheckFails_whenMarkingConflict_thenRollsBackMappingChange() {
        // given
        Fixture fixture = fixture(false);

        // when, then
        assertThatThrownBy(() -> pagePersistenceService.persistOwnedNormalizedPage(
                fixture.jobId(),
                fixture.integrationId(),
                fixture.accountId(),
                WORKER_TOKEN,
                conflictingPage(fixture.externalEventId())
        )).isInstanceOf(GoogleOperationOwnershipLostException.class);

        assertThat(eventMappingRepository.findById(fixture.mappingId()))
                .get()
                .extracting(GoogleCalendarEventMapping::getSyncStatus)
                .isEqualTo(GoogleCalendarMappingSyncStatus.ACTIVE);
        assertThat(jobRepository.findById(fixture.jobId()))
                .get()
                .satisfies(job -> assertThat(job.isMappingConflictDetected()).isFalse());
    }

    private Fixture fixture(boolean syncJob) {
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(new Tag(
                TagType.DEFAULT, "기타", "#64748B"
        ));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                new GoogleCalendarIntegration(
                        account.getId(),
                        "google-subject-" + account.getId(),
                        "user@example.com",
                        "encrypted-refresh-token",
                        "encrypted-access-token",
                        Instant.now().plusSeconds(3_600),
                        Instant.now()
                )
        );
        Event event = eventRepository.saveAndFlush(new Event(
                "Baseline title",
                null,
                SCHEDULE.startAt(),
                SCHEDULE.endAt(),
                SCHEDULE.allDay(),
                SCHEDULE.timeZone(),
                null,
                tag,
                account
        ));
        GoogleCalendarEventMapping mapping = eventMappingRepository.saveAndFlush(
                new GoogleCalendarEventMapping(
                        integration,
                        event,
                        "external-event-" + event.getId(),
                        new GoogleCalendarItemSnapshot(
                                "etag-baseline",
                                Instant.parse("2026-08-05T00:00:00Z"),
                                GoogleCalendarContentHasher.hashEvent(event)
                        )
                )
        );
        event.replace(
                "Local title",
                null,
                SCHEDULE.startAt(),
                SCHEDULE.endAt(),
                SCHEDULE.allDay(),
                SCHEDULE.timeZone()
        );
        eventRepository.saveAndFlush(event);
        GoogleOperationJob job = syncJob
                ? GoogleOperationJob.sync(
                        "operation-" + event.getId(),
                        integration.getId(),
                        account.getId(),
                        1L,
                        GoogleOperationJobTrigger.MANUAL,
                        Instant.now().minusSeconds(60)
                )
                : GoogleOperationJob.outbound(
                        "operation-" + event.getId(),
                        integration.getId(),
                        account.getId(),
                        1L,
                        "EVENT_UPSERT",
                        GoogleCalendarSyncTarget.event(event.getId()),
                        mapping.getExternalEventId(),
                        "{}",
                        GoogleContentHash.digest("TEST", "desired"),
                        Instant.now().minusSeconds(60)
                );
        jobRepository.saveAndFlush(job);
        assertThat(jobPersistenceService.acquireLease(account.getId(), WORKER_TOKEN)).isTrue();
        GoogleOperationJob claimed = jobPersistenceService.claimHead(
                account.getId(), WORKER_TOKEN
        );
        syncLeaseService.acquire(account.getId(), WORKER_TOKEN);
        return new Fixture(
                account.getId(), integration.getId(), event.getId(), mapping.getId(),
                claimed.getId(), mapping.getExternalEventId()
        );
    }

    private GoogleCalendarNormalizedPage conflictingPage(String externalEventId) {
        EventUpsert googleChange = new EventUpsert(
                externalEventId,
                "etag-google",
                Instant.parse("2026-08-05T02:00:00Z"),
                "Google title",
                null,
                SCHEDULE
        );
        return new GoogleCalendarNormalizedPage(List.of(googleChange), null, null);
    }

    private record Fixture(
            Long accountId,
            Long integrationId,
            Long eventId,
            Long mappingId,
            Long jobId,
            String externalEventId
    ) {
    }
}
