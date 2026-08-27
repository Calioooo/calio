package com.calio.calendar.sharing.service;

import static org.mockito.Mockito.inOrder;

import com.calio.calendar.sharing.event.service.PersonalEventGroupShareCommandService;
import com.calio.calendar.sharing.recurrence.service.PersonalRecurrenceGroupShareCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class PersonalScheduleGroupShareCleanupAdapterTest {

    @Test
    @DisplayName("멤버 lifecycle cleanup은 해당 Group Space와 멤버의 단건·반복 mapping만 정리한다")
    void cleanupMemberSharesDelegatesToBothSourceTypesWithMemberScope() {
        PersonalEventGroupShareCommandService eventCommandService = Mockito.mock(
                PersonalEventGroupShareCommandService.class
        );
        PersonalRecurrenceGroupShareCommandService recurrenceCommandService = Mockito.mock(
                PersonalRecurrenceGroupShareCommandService.class
        );
        PersonalScheduleGroupShareCleanupAdapter adapter = new PersonalScheduleGroupShareCleanupAdapter(
                eventCommandService,
                recurrenceCommandService
        );

        adapter.cleanupMemberShares(10L, 20L);

        InOrder order = inOrder(eventCommandService, recurrenceCommandService);
        order.verify(eventCommandService).deleteAllForMemberInGroupSpace(10L, 20L);
        order.verify(recurrenceCommandService).deleteAllForMemberInGroupSpace(10L, 20L);
    }

    @Test
    @DisplayName("Group Space lifecycle cleanup은 해당 Group Space의 모든 mapping을 정리한다")
    void cleanupGroupSharesDelegatesToBothSourceTypesWithGroupScope() {
        PersonalEventGroupShareCommandService eventCommandService = Mockito.mock(
                PersonalEventGroupShareCommandService.class
        );
        PersonalRecurrenceGroupShareCommandService recurrenceCommandService = Mockito.mock(
                PersonalRecurrenceGroupShareCommandService.class
        );
        PersonalScheduleGroupShareCleanupAdapter adapter = new PersonalScheduleGroupShareCleanupAdapter(
                eventCommandService,
                recurrenceCommandService
        );

        adapter.cleanupGroupShares(10L);

        InOrder order = inOrder(eventCommandService, recurrenceCommandService);
        order.verify(eventCommandService).deleteAllForGroupSpace(10L);
        order.verify(recurrenceCommandService).deleteAllForGroupSpace(10L);
    }
}
