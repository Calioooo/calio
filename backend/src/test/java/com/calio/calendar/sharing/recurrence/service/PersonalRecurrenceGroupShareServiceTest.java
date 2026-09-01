package com.calio.calendar.sharing.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.sharing.controller.dto.GroupShareTargetStatus;
import com.calio.calendar.sharing.controller.dto.GroupShareStatusResponse;
import com.calio.calendar.sharing.controller.dto.UpdateGroupShareAnonymousRequest;
import com.calio.calendar.sharing.recurrence.controller.dto.CreateRecurrenceGroupSharesRequest;
import com.calio.calendar.sharing.recurrence.controller.dto.CreateRecurrenceGroupSharesResponse;
import com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import java.time.Instant;
import java.util.List;
import org.mockito.ArgumentCaptor;
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
        ArgumentCaptor<PersonalRecurrenceGroupShare> shareCaptor = ArgumentCaptor.forClass(
                PersonalRecurrenceGroupShare.class
        );
        verify(shareCommandService).createIfAbsent(shareCaptor.capture());
        assertThat(shareCaptor.getValue().isAnonymous()).isTrue();
    }

    @Test
    @DisplayName("반복 일정 원본 소유자는 대상별 공유 상태를 조회한다")
    void listReturnsTargetSpecificShareStatesForSourceOwner() {
        RecurrenceEvent recurrenceEvent = mock(RecurrenceEvent.class);
        GroupSpace groupSpace = groupSpace(10L);
        var share = com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare.create(
                recurrenceEvent,
                groupSpace,
                false
        );
        when(shareQueryService.listByRecurrenceEventId(30L)).thenReturn(List.of(share));

        List<GroupShareStatusResponse> response = service.list(30L);

        assertThat(response).singleElement().satisfies(status -> {
            assertThat(status.groupSpaceId()).isEqualTo(10L);
            assertThat(status.groupSpaceName()).isEqualTo("group");
            assertThat(status.isAnonymous()).isFalse();
            assertThat(status.publicShareId()).isEqualTo(share.getPublicShareId());
        });
    }

    @Test
    @DisplayName("반복 일정 원본 소유자는 한 대상의 익명 여부만 변경한다")
    void changeAnonymousUpdatesOnlyRequestedTargetMapping() {
        RecurrenceEvent recurrenceEvent = mock(RecurrenceEvent.class);
        GroupSpace firstGroup = groupSpace(10L);
        GroupSpace secondGroup = groupSpace(20L);
        var firstShare = com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare.create(
                recurrenceEvent, firstGroup, true
        );
        var secondShare = com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare.create(
                recurrenceEvent, secondGroup, true
        );
        when(shareQueryService.getByRecurrenceEventIdAndGroupSpaceId(30L, 10L)).thenReturn(firstShare);
        doAnswer(invocation -> {
            invocation.<com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare>getArgument(0)
                    .changeAnonymous(invocation.getArgument(1));
            return null;
        }).when(shareCommandService).changeAnonymous(firstShare, false);

        GroupShareStatusResponse response = service.changeAnonymous(
                30L, 10L, new UpdateGroupShareAnonymousRequest(false)
        );

        assertThat(response.isAnonymous()).isFalse();
        assertThat(firstShare.isAnonymous()).isFalse();
        assertThat(secondShare.isAnonymous()).isTrue();
        verify(shareCommandService).changeAnonymous(firstShare, false);
    }

    @Test
    @DisplayName("반복 일정 원본 소유자는 지정한 대상 mapping만 해제한다")
    void removeDeletesOnlyRequestedTargetMapping() {
        RecurrenceEvent recurrenceEvent = mock(RecurrenceEvent.class);
        var requestedShare = com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare.create(
                recurrenceEvent, groupSpace(10L), false
        );
        var remainingShare = com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare.create(
                recurrenceEvent, groupSpace(20L), false
        );
        when(shareQueryService.getByRecurrenceEventIdAndGroupSpaceId(30L, 10L))
                .thenReturn(requestedShare);

        service.remove(30L, 10L);

        verify(shareCommandService).delete(requestedShare);
        verify(shareCommandService, never()).delete(remainingShare);
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
