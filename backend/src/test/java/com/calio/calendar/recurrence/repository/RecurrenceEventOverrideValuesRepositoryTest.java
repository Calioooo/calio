package com.calio.calendar.recurrence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.testsupport.SharedIntegrationDatabase;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.dto.RecurrenceOverrideView;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:calendar-shared-integration-test;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@SharedIntegrationDatabase
@Transactional
class RecurrenceEventOverrideValuesRepositoryTest {

  @Autowired private AccountRepository accountRepository;

  @Autowired private TagRepository tagRepository;

  @Autowired private RecurrenceEventRepository recurrenceEventRepository;

  @Autowired private EntityManager entityManager;

  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("활성 기간과 겹치는 override만 조회하고 master를 함께 로딩한다")
  void givenActiveAndDeletedOverrides_whenFindActiveOverlapping_thenReturnsLoadedActiveOverride() {
    Account account = accountRepository.save(new Account());
    Tag tag = tagRepository.save(Tag.personalDefault("기타", "#64748B"));
    RecurrenceEvent recurrenceEvent =
        recurrenceEventRepository.save(
            new RecurrenceEvent(
                "Rule",
                null,
                RecurrenceSchedule.create(
                    false,
                    Instant.parse("2027-01-01T09:00:00Z"),
                    Instant.parse("2027-01-01T10:00:00Z"),
                    "UTC"),
                List.of("RRULE:FREQ=DAILY"),
                tag.getId(),
                account.getId()));
    RecurrenceEventOverride activeOverride =
        recurrenceEvent.changeOccurrence(
            Instant.parse("2027-01-02T09:00:00Z"),
            "Moved",
            null,
            CanonicalSchedule.recurrenceOverride(
                Instant.parse("2027-01-02T10:00:00Z"),
                Instant.parse("2027-01-02T11:00:00Z"),
                false,
                "UTC"));
    recurrenceEvent.excludeOccurrence(
        Instant.parse("2027-01-03T09:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"));
    recurrenceEventRepository.flush();
    entityManager.clear();

    List<RecurrenceOverrideView> overrides =
        recurrenceEventRepository.findActiveOverlappingOverrides(
            account.getId(),
            Instant.parse("2027-01-02T09:30:00Z"),
            Instant.parse("2027-01-02T11:30:00Z"));

    assertThat(overrides)
        .extracting(view -> view.override().getOriginStartAt())
        .containsExactly(activeOverride.getOriginStartAt());
    RecurrenceOverrideView loadedOverride = overrides.getFirst();
    assertThat(loadedOverride.override()).isEqualTo(activeOverride);
    assertThat(loadedOverride.recurrenceEvent().getTagId()).isEqualTo(tag.getId());
  }

  @Test
  @DisplayName("Root에서 원래 시작값으로 제거한 개별 변경만 삭제하고 다른 기록은 보존한다")
  void removeOverridesThroughRootKeepsOtherIdentities() {
    Account account = accountRepository.save(new Account());
    Tag tag = tagRepository.save(Tag.personalDefault("기타", "#64748B"));
    Instant firstStart = Instant.parse("2027-02-01T09:00:00Z");
    RecurrenceSchedule schedule =
        RecurrenceSchedule.create(false, firstStart, firstStart.plusSeconds(3600), "UTC");
    RecurrenceEvent first =
        recurrenceEventRepository.save(
            new RecurrenceEvent(
                "First",
                null,
                schedule,
                List.of("RRULE:FREQ=DAILY"),
                tag.getId(),
                account.getId()));
    RecurrenceEvent second =
        recurrenceEventRepository.save(
            new RecurrenceEvent(
                "Second",
                null,
                schedule,
                List.of("RRULE:FREQ=DAILY"),
                tag.getId(),
                account.getId()));
    Instant removedOrigin = firstStart;
    Instant retainedOrigin = firstStart.plusSeconds(86400);
    first.excludeOccurrence(removedOrigin, firstStart);
    first.excludeOccurrence(retainedOrigin, firstStart);
    second.excludeOccurrence(removedOrigin, firstStart);
    recurrenceEventRepository.flush();
    entityManager.clear();

    RecurrenceEvent loadedFirst = recurrenceEventRepository.findById(first.getId()).orElseThrow();
    loadedFirst.removeOverrides(List.of(removedOrigin));
    recurrenceEventRepository.flush();
    entityManager.clear();

    assertThat(
            recurrenceEventRepository.findOverrideByRecurrenceIdAndOriginStartAt(
                first.getId(), removedOrigin))
        .isEmpty();
    assertThat(
            recurrenceEventRepository.findOverrideByRecurrenceIdAndOriginStartAt(
                first.getId(), retainedOrigin))
        .isPresent();
    assertThat(
            recurrenceEventRepository.findOverrideByRecurrenceIdAndOriginStartAt(
                second.getId(), removedOrigin))
        .isPresent();
  }

  @Test
  @DisplayName("Root 저장 후 같은 origin의 수정·제외·복원은 같은 회차의 변경 값을 교체해 저장한다")
  void saveAndRestoreOverrideThroughRootPersistsReplacement() {
    RecurrenceEvent recurrenceEvent = newRecurrenceEvent();
    Instant origin = recurrenceEvent.getFirstOccurrenceStartAt();
    CanonicalSchedule schedule =
        CanonicalSchedule.recurrenceOverride(
            origin.plusSeconds(7200), origin.plusSeconds(10800), false, "UTC");
    RecurrenceEventOverride created =
        recurrenceEvent.changeOccurrence(origin, "Moved", null, schedule);
    recurrenceEventRepository.saveAndFlush(recurrenceEvent);
    Long recurrenceId = recurrenceEvent.getId();
    assertThat(
            recurrenceEventRepository.findOverrideByRecurrenceIdAndOriginStartAt(
                recurrenceId, origin))
        .contains(created);
    entityManager.clear();

    RecurrenceEvent loaded = recurrenceEventRepository.findById(recurrenceId).orElseThrow();
    loaded.excludeOccurrence(origin, origin.plusSeconds(86400));
    recurrenceEventRepository.flush();
    entityManager.clear();
    loaded = recurrenceEventRepository.findById(recurrenceId).orElseThrow();
    assertThat(loaded.findOverride(origin).orElseThrow().isDeleted()).isTrue();
    RecurrenceEventOverride replacement =
        loaded.changeOccurrence(origin, "Restored", "memo", schedule);
    recurrenceEventRepository.flush();
    entityManager.clear();

    RecurrenceEvent reloaded = recurrenceEventRepository.findById(recurrenceId).orElseThrow();
    assertThat(reloaded.getOverrides()).hasSize(1);
    RecurrenceEventOverride restored = reloaded.findOverride(origin).orElseThrow();
    assertThat(restored).isEqualTo(replacement);
    assertThat(created.getOverrideTitle()).isEqualTo("Moved");
    assertThat(restored.getOverrideTitle()).isEqualTo("Restored");
    assertThat(restored.getOverrideDescription()).isEqualTo("memo");
    assertThat(restored.isDeleted()).isFalse();
  }

  @Test
  @DisplayName("Root를 삭제하면 활성·제외 값도 삭제하고 다른 Root의 같은 origin은 보존한다")
  void deleteRootRemovesOwnedOverridesOnly() {
    RecurrenceEvent first = newRecurrenceEvent();
    RecurrenceEvent second = newRecurrenceEvent();
    Instant origin = first.getFirstOccurrenceStartAt();
    first.changeOccurrence(
        origin,
        "Moved",
        null,
        CanonicalSchedule.recurrenceOverride(origin, origin.plusSeconds(3600), false, "UTC"));
    first.excludeOccurrence(origin.plusSeconds(86400), origin);
    second.excludeOccurrence(origin, origin);
    recurrenceEventRepository.saveAndFlush(first);
    recurrenceEventRepository.saveAndFlush(second);
    entityManager.clear();

    recurrenceEventRepository.deleteById(first.getId());
    recurrenceEventRepository.flush();
    entityManager.clear();

    assertThat(recurrenceEventRepository.findById(first.getId())).isEmpty();
    assertThat(
            recurrenceEventRepository.findOverrideByRecurrenceIdAndOriginStartAt(
                first.getId(), origin))
        .isEmpty();
    assertThat(
            recurrenceEventRepository.findOverrideByRecurrenceIdAndOriginStartAt(
                first.getId(), origin.plusSeconds(86400)))
        .isEmpty();
    assertThat(recurrenceEventRepository.findById(second.getId())).isPresent();
    assertThat(
            recurrenceEventRepository.findOverrideByRecurrenceIdAndOriginStartAt(
                second.getId(), origin))
        .isPresent();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("앱과 동기화가 다른 회차를 동시에 변경해도 Root 잠금으로 두 변경 값을 모두 보존한다")
  void concurrentOccurrenceChangesPreserveBothValues() throws Exception {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    RecurrenceEvent event =
        transaction.execute(status -> recurrenceEventRepository.saveAndFlush(newRecurrenceEvent()));
    Instant firstOrigin = event.getFirstOccurrenceStartAt();
    Instant secondOrigin = firstOrigin.plusSeconds(86400);
    CountDownLatch firstLocked = new CountDownLatch(1);
    CountDownLatch secondAttempted = new CountDownLatch(1);
    CountDownLatch secondLocked = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> first =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        RecurrenceEvent root =
                            recurrenceEventRepository
                                .findByIdAndAccountIdForUpdate(event.getId(), event.getAccountId())
                                .orElseThrow();
                        root.excludeOccurrence(firstOrigin, firstOrigin);
                        firstLocked.countDown();
                        await(releaseFirst);
                      }));
      try {
        assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
        Future<?> second =
            executor.submit(
                () ->
                    transaction.executeWithoutResult(
                        status -> {
                          secondAttempted.countDown();
                          RecurrenceEvent root =
                              recurrenceEventRepository
                                  .findByIdForUpdate(event.getId())
                                  .orElseThrow();
                          secondLocked.countDown();
                          root.excludeOccurrence(secondOrigin, firstOrigin);
                        }));
        assertThat(secondAttempted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(secondLocked.await(250, TimeUnit.MILLISECONDS)).isFalse();
        releaseFirst.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
      } finally {
        releaseFirst.countDown();
      }
    }
    List<RecurrenceEventOverride> saved =
        transaction.execute(
            status ->
                recurrenceEventRepository.findById(event.getId()).orElseThrow().getOverrides());
    assertThat(saved)
        .extracting(RecurrenceEventOverride::getOriginStartAt)
        .containsExactlyInAnyOrder(firstOrigin, secondOrigin);
    assertThat(saved).allMatch(RecurrenceEventOverride::isDeleted);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("다른 트랜잭션의 작업을 기다리지 못했다.");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private RecurrenceEvent newRecurrenceEvent() {
    Account account = accountRepository.save(new Account());
    Tag tag = tagRepository.save(Tag.personalDefault("기타", "#64748B"));
    Instant start = Instant.parse("2027-02-01T09:00:00Z");
    return new RecurrenceEvent(
        "Rule",
        null,
        RecurrenceSchedule.create(false, start, start.plusSeconds(3600), "UTC"),
        List.of("RRULE:FREQ=DAILY"),
        tag.getId(),
        account.getId());
  }
}
