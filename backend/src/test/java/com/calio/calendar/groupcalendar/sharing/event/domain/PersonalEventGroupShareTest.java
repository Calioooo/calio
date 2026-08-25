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
    @DisplayName("새 공유 mapping은 원본 상세를 숨긴다")
    void givenNewShare_whenCreated_thenHidesOriginalDetails() {
        // when
        PersonalEventGroupShare share = new PersonalEventGroupShare(event(), null);

        // then
        assertThat(share.isShowOriginalDetails()).isFalse();
    }

    @Test
    @DisplayName("공유 mapping은 원본 상세 공개 여부만 변경할 수 있다")
    void givenShare_whenChangeOriginalDetailsVisibility_thenUpdatesPrivacyOnly() {
        // given
        PersonalEventGroupShare share = new PersonalEventGroupShare(event(), null);

        // when
        share.changeOriginalDetailsVisibility(true);

        // then
        assertThat(share.isShowOriginalDetails()).isTrue();
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
