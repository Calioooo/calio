package com.calio.calendar.sharing.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.sharing.controller.dto.GroupShareTargetStatus;
import com.calio.calendar.sharing.recurrence.controller.dto.CreateRecurrenceGroupSharesRequest;
import com.calio.calendar.sharing.recurrence.controller.dto.CreateRecurrenceGroupSharesResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PersonalRecurrenceGroupShareServiceTest {

    private final RecurrenceEventQueryService recurrenceEventQueryService = mock(RecurrenceEventQueryService.class);
    private final GroupMembershipQueryService membershipQueryService = mock(GroupMembershipQueryService.class);
    private final PersonalRecurrenceGroupShareQueryService shareQueryService = mock(
            PersonalRecurrenceGroupShareQueryService.class
    );
    private final PersonalRecurrenceGroupShareCommandService shareCommandService = mock(
            PersonalRecurrenceGroupShareCommandService.class
    );
    private final PersonalRecurrenceGroupShareService service = new PersonalRecurrenceGroupShareService(
            recurrenceEventQueryService,
            membershipQueryService,
            shareQueryService,
            shareCommandService
    );

    @Test
    @DisplayName("반복 master 전체 공유는 중복 target을 한 번 처리하고 초기 익명 여부를 전달한다")
    void createSharesWholeMasterOncePerDistinctActiveTarget() {
        RecurrenceEvent recurrenceEvent = mock(RecurrenceEvent.class);
        when(recurrenceEvent.getId()).thenReturn(30L);
        GroupSpace groupSpace = groupSpace(10L);
        when(recurrenceEventQueryService.getRecurrenceEvent(100L, 30L)).thenReturn(recurrenceEvent);
        when(membershipQueryService.listActiveMemberships(100L, List.of(10L, 20L)))
                .thenReturn(List.of(member(groupSpace)));
        when(shareQueryService.listExistingShares(List.of(30L), List.of(10L, 20L))).thenReturn(List.of());
        when(shareCommandService.createIfAbsent(any())).thenReturn(true);

        CreateRecurrenceGroupSharesResponse response = service.create(100L, 30L,
                new CreateRecurrenceGroupSharesRequest(List.of(10L, 20L, 10L), true));

        assertThat(response.recurrenceId()).isEqualTo(30L);
        assertThat(response.targets()).extracting(target -> target.status())
                .containsExactly(GroupShareTargetStatus.SHARED, GroupShareTargetStatus.NOT_ELIGIBLE);
        verify(shareCommandService).createIfAbsent(any());
    }

    private GroupSpace groupSpace(Long id) {
        GroupSpace groupSpace = new GroupSpace(100L, "group", null);
        ReflectionTestUtils.setField(groupSpace, "id", id);
        return groupSpace;
    }

    private GroupMember member(GroupSpace groupSpace) {
        return new GroupMember(groupSpace, 100L, "member", Instant.parse("2026-08-01T00:00:00Z"));
    }
}
