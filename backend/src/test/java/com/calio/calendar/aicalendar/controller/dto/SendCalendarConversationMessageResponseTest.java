package com.calio.calendar.aicalendar.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.aicalendar.domain.CalendarAssistantBlockType;
import com.calio.calendar.aicalendar.domain.CalendarMutationScope;
import com.calio.calendar.aicalendar.domain.CalendarMutationType;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantAnswer;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationPreview;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.dto.CalendarFreeTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SendCalendarConversationMessageResponseTest {

    @Test
    @DisplayName("agent의 실제 tool 결과는 EVENTS와 FREE_TIMES 블록으로 함께 반환한다")
    void givenAgentAnswerWithToolResults_whenCreateResponse_thenIncludesTypedBlocks() {
        // given
        CalendarAssistantAnswer answer = new CalendarAssistantAnswer(
                "내일 오후에 시간이 비어 있어요.",
                List.of(event()),
                List.of(new CalendarFreeTime(
                        "2026-07-01T09:00:00Z",
                        "2026-07-01T10:00:00Z",
                        List.of("Holiday")
                )),
                List.of(new CalendarMutationPreview(
                        CalendarMutationType.UPDATE,
                        CalendarMutationScope.EVENT,
                        event(),
                        updatedEvent()
                ))
        );

        // when
        SendCalendarConversationMessageResponse response =
                SendCalendarConversationMessageResponse.from("conversation-id", answer);

        // then
        assertThat(response.assistantMessage()).isEqualTo("내일 오후에 시간이 비어 있어요.");
        assertThat(response.blocks()).hasSize(3);
        assertThat(response.blocks().get(0).type()).isEqualTo(CalendarAssistantBlockType.EVENTS);
        assertThat(response.blocks().get(0).items()).isEqualTo(List.of(event()));
        assertThat(response.blocks().get(1).type()).isEqualTo(CalendarAssistantBlockType.FREE_TIMES);
        assertThat(response.blocks().get(1).items()).isEqualTo(List.of(new FreeTimeResponse(
                "2026-07-01T09:00:00Z",
                "2026-07-01T10:00:00Z",
                List.of("Holiday")
        )));
        assertThat(response.blocks().get(2).type()).isEqualTo(CalendarAssistantBlockType.MUTATION_PREVIEW);
        assertThat(response.blocks().get(2).items()).singleElement().satisfies(preview -> {
            CalendarMutationPreviewResponse mutationPreview = (CalendarMutationPreviewResponse) preview;
            assertThat(mutationPreview.type()).isEqualTo(CalendarMutationType.UPDATE);
            assertThat(mutationPreview.scope()).isEqualTo(CalendarMutationScope.EVENT);
            assertThat(mutationPreview.before().title()).isEqualTo("Planning");
            assertThat(mutationPreview.after().startAt()).isEqualTo(Instant.parse("2026-07-01T11:00:00Z"));
        });
    }

    private EventResponse event() {
        return new EventResponse(
                1L,
                "Planning",
                "Planning details",
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T11:00:00Z"),
                false,
                "UTC",
                false,
                null,
                false,
                null,
                null,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z")
        );
    }

    private EventResponse updatedEvent() {
        return new EventResponse(
                1L,
                "Planning",
                "Planning details",
                Instant.parse("2026-07-01T11:00:00Z"),
                Instant.parse("2026-07-01T12:00:00Z"),
                false,
                "UTC",
                false,
                null,
                false,
                null,
                null,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z")
        );
    }
}
