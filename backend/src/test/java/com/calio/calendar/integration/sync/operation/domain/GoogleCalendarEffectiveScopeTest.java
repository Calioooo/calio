package com.calio.calendar.integration.sync.operation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarEffectiveScopeTest {

    @Test
    @DisplayName("일반 Event scope는 Event 종류와 canonical ID를 저장값으로 사용한다")
    void givenEventId_whenCreateEventScope_thenUsesEventStorageValues() {
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.event(10L);

        assertThat(scope.type()).isEqualTo(GoogleCalendarEffectiveScopeType.EVENT);
        assertThat(scope.storedScope()).isEqualTo("GENERAL_EVENT");
        assertThat(scope.storedKey()).isEqualTo("10");
        assertThat(scope.isRecurrenceEventAggregate()).isFalse();
    }

    @Test
    @DisplayName("recurrence-event aggregate scope는 하위 override key prefix를 제공한다")
    void givenRecurrenceEventId_whenCreateAggregateScope_thenProvidesChildOverridePrefix() {
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.recurrenceEvent(20L);

        assertThat(scope.type()).isEqualTo(GoogleCalendarEffectiveScopeType.RECURRENCE_EVENT);
        assertThat(scope.storedKey()).isEqualTo("20");
        assertThat(scope.isRecurrenceEventAggregate()).isTrue();
        assertThat(scope.childOverrideKeyPrefix()).isEqualTo("20:");
    }

    @Test
    @DisplayName("recurrence override scope는 parent ID와 origin start를 하나의 저장 key로 사용한다")
    void givenOverrideIdentity_whenCreateOverrideScope_thenUsesExactOverrideStorageKey() {
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.recurrenceOverride(
                20L,
                Instant.parse("2026-08-18T00:00:00Z")
        );

        assertThat(scope.type()).isEqualTo(GoogleCalendarEffectiveScopeType.RECURRENCE_OVERRIDE);
        assertThat(scope.storedKey()).isEqualTo("20:2026-08-18T00:00:00Z");
        assertThatThrownBy(scope::childOverrideKeyPrefix)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("scope는 type, canonical ID, origin start가 모두 같을 때만 동등하다")
    void givenSameScopeValues_whenCompare_thenUsesValueEquality() {
        GoogleCalendarEffectiveScope event = GoogleCalendarEffectiveScope.event(10L);
        GoogleCalendarEffectiveScope sameEvent = GoogleCalendarEffectiveScope.event(10L);
        GoogleCalendarEffectiveScope differentType = GoogleCalendarEffectiveScope.recurrenceEvent(10L);
        GoogleCalendarEffectiveScope differentId = GoogleCalendarEffectiveScope.event(11L);
        GoogleCalendarEffectiveScope override = GoogleCalendarEffectiveScope.recurrenceOverride(
                10L, Instant.parse("2026-08-18T00:00:00Z"));
        GoogleCalendarEffectiveScope differentOriginOverride = GoogleCalendarEffectiveScope.recurrenceOverride(
                10L, Instant.parse("2026-08-19T00:00:00Z"));

        assertThat(event).isEqualTo(sameEvent).hasSameHashCodeAs(sameEvent);
        assertThat(event).isNotEqualTo(differentType).isNotEqualTo(differentId)
                .isNotEqualTo(null).isNotEqualTo("event");
        assertThat(override).isNotEqualTo(differentOriginOverride);
    }
}
