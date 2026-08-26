package com.calio.calendar.groupcalendar.sharing.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.event.domain.Event;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonalEventGroupShareTest {

    private static final Instant START_AT = Instant.parse("2028-01-01T09:00:00Z");
    private static final Instant END_AT = Instant.parse("2028-01-01T10:00:00Z");

    @Test
    @DisplayName("새 공유 mapping은 익명 노출과 변경되지 않는 공개 UUID를 가진다")
    void givenNewShare_whenCreated_thenIsAnonymousWithPublicId() {
        // when
        PersonalEventGroupShare share = new PersonalEventGroupShare(event(), null);

        // then
        assertThat(share.isAnonymous()).isTrue();
        assertThat(share.getPublicShareId()).isNotNull();
    }

    @Test
    @DisplayName("익명 공유는 원본의 시간은 유지하고 제목과 설명만 숨긴다")
    void givenAnonymousShare_whenResolved_thenKeepsSourceScheduleAndHidesDetails() {
        // given
        PersonalEventGroupShare share = new PersonalEventGroupShare(event(), null);

        // then
        assertThat(share.resolvePublicTitle("익명 일정")).isEqualTo("익명 일정");
        assertThat(share.resolvePublicDescription()).isNull();
        assertThat(share.resolveStartAt()).isEqualTo(START_AT);
        assertThat(share.resolveEndAt()).isEqualTo(END_AT);
        assertThat(share.resolveAllDay()).isFalse();
    }

    private Event event() {
        return new Event(
                "원본 일정",
                "원본 설명",
                START_AT,
                END_AT,
                false,
                "UTC",
                null,
                null,
                null
        );
    }
}
