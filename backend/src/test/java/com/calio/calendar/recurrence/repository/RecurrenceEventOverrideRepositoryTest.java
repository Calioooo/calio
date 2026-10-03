package com.calio.calendar.recurrence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.testsupport.SharedIntegrationDatabase;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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
class RecurrenceEventOverrideRepositoryTest {

  @Autowired private AccountRepository accountRepository;

  @Autowired private TagRepository tagRepository;

  @Autowired private RecurrenceEventRepository recurrenceEventRepository;

  @Autowired private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("활성 기간과 겹치는 override만 조회하고 master를 함께 로딩한다")
  void givenActiveAndDeletedOverrides_whenFindActiveOverlapping_thenReturnsLoadedActiveOverride() {
    // given
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
            true,
            "Moved",
            null,
            CanonicalSchedule.recurrenceOverride(
                Instant.parse("2027-01-02T10:00:00Z"),
                Instant.parse("2027-01-02T11:00:00Z"),
                false,
                "UTC"));
    recurrenceEvent.excludeOccurrence(
        Instant.parse("2027-01-03T09:00:00Z"), true, Instant.parse("2027-01-01T00:00:00Z"));
    recurrenceEventRepository.flush();
    entityManager.clear();

    // when
    List<RecurrenceEventOverride> overrides =
        recurrenceEventOverrideRepository.findActiveOverlappingOverrides(
            account.getId(),
            Instant.parse("2027-01-02T09:30:00Z"),
            Instant.parse("2027-01-02T11:30:00Z"));

    // then
    assertThat(overrides)
        .extracting(RecurrenceEventOverride::getOverrideId)
        .containsExactly(activeOverride.getOverrideId());
    RecurrenceEventOverride loadedOverride = overrides.getFirst();
    assertThat(
            entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(loadedOverride, "recurrenceEvent"))
        .isTrue();
    assertThat(loadedOverride.getRecurrenceEvent().getTagId()).isEqualTo(tag.getId());
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
    first.excludeOccurrence(removedOrigin, true, firstStart);
    first.excludeOccurrence(retainedOrigin, true, firstStart);
    second.excludeOccurrence(removedOrigin, true, firstStart);
    recurrenceEventRepository.flush();
    entityManager.clear();

    RecurrenceEvent loadedFirst = recurrenceEventRepository.findById(first.getId()).orElseThrow();
    loadedFirst.removeOverrides(List.of(removedOrigin));
    recurrenceEventRepository.flush();
    entityManager.clear();

    assertThat(
            recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                first.getId(), removedOrigin))
        .isEmpty();
    assertThat(
            recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                first.getId(), retainedOrigin))
        .isPresent();
    assertThat(
            recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                second.getId(), removedOrigin))
        .isPresent();
  }

  @Test
  @DisplayName("Root 저장 후 같은 origin의 수정·제외·복원은 하나의 자식 identity를 유지한다")
  void saveAndRestoreOverrideThroughRootKeepsPersistedIdentity() {
    // given
    RecurrenceEvent recurrenceEvent = newRecurrenceEvent();
    Instant origin = recurrenceEvent.getFirstOccurrenceStartAt();
    CanonicalSchedule schedule =
        CanonicalSchedule.recurrenceOverride(
            origin.plusSeconds(7200), origin.plusSeconds(10800), false, "UTC");
    RecurrenceEventOverride created =
        recurrenceEvent.changeOccurrence(origin, true, "Moved", null, schedule);
    recurrenceEventRepository.saveAndFlush(recurrenceEvent);
    Long recurrenceId = recurrenceEvent.getId();
    Long overrideId = created.getOverrideId();
    assertThat(overrideId).isNotNull();
    entityManager.clear();

    // when
    RecurrenceEvent loaded = recurrenceEventRepository.findById(recurrenceId).orElseThrow();
    loaded.excludeOccurrence(origin, false, origin.plusSeconds(86400));
    loaded.changeOccurrence(origin, false, "Restored", "memo", schedule);
    recurrenceEventRepository.flush();
    entityManager.clear();

    // then
    RecurrenceEvent reloaded = recurrenceEventRepository.findById(recurrenceId).orElseThrow();
    assertThat(reloaded.getOverrides()).hasSize(1);
    RecurrenceEventOverride restored = reloaded.findOverride(origin).orElseThrow();
    assertThat(restored.getOverrideId()).isEqualTo(overrideId);
    assertThat(restored.getOverrideTitle()).isEqualTo("Restored");
    assertThat(restored.getOverrideDescription()).isEqualTo("memo");
    assertThat(restored.isDeleted()).isFalse();
  }

  @Test
  @DisplayName("Root를 삭제하면 활성·제외 자식도 삭제하고 다른 Root의 같은 origin은 보존한다")
  void deleteRootRemovesOwnedOverridesOnly() {
    // given
    RecurrenceEvent first = newRecurrenceEvent();
    RecurrenceEvent second = newRecurrenceEvent();
    Instant origin = first.getFirstOccurrenceStartAt();
    first.changeOccurrence(
        origin,
        true,
        "Moved",
        null,
        CanonicalSchedule.recurrenceOverride(origin, origin.plusSeconds(3600), false, "UTC"));
    first.excludeOccurrence(origin.plusSeconds(86400), true, origin);
    second.excludeOccurrence(origin, true, origin);
    recurrenceEventRepository.saveAndFlush(first);
    recurrenceEventRepository.saveAndFlush(second);
    entityManager.clear();

    // when
    recurrenceEventRepository.deleteById(first.getId());
    recurrenceEventRepository.flush();
    entityManager.clear();

    // then
    assertThat(recurrenceEventRepository.findById(first.getId())).isEmpty();
    assertThat(
            recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                first.getId(), origin))
        .isEmpty();
    assertThat(
            recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                first.getId(), origin.plusSeconds(86400)))
        .isEmpty();
    assertThat(recurrenceEventRepository.findById(second.getId())).isPresent();
    assertThat(
            recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(
                second.getId(), origin))
        .isPresent();
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
