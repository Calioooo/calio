package com.calio.calendar.sharing.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.service.EventQueryService;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.sharing.controller.dto.GroupShareTargetStatus;
import com.calio.calendar.sharing.controller.dto.GroupShareStatusResponse;
import com.calio.calendar.sharing.controller.dto.UpdateGroupShareAnonymousRequest;
import com.calio.calendar.sharing.event.controller.dto.CreateEventGroupSharesRequest;
import com.calio.calendar.sharing.event.controller.dto.CreateEventGroupSharesResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PersonalEventGroupShareServiceTest {

    private final EventQueryService eventQueryService = mock(EventQueryService.class);
    private final GroupMembershipQueryService membershipQueryService = mock(GroupMembershipQueryService.class);
    private final PersonalEventGroupShareQueryService shareQueryService = mock(
            PersonalEventGroupShareQueryService.class
    );
    private final PersonalEventGroupShareCommandService shareCommandService = mock(
            PersonalEventGroupShareCommandService.class
    );
    private final PersonalEventGroupShareService service = new PersonalEventGroupShareService(
            eventQueryService,
            membershipQueryService,
            shareQueryService,
            shareCommandService
    );

    @Test
    @DisplayName("유효 대상은 공유하고 비활성 대상은 전체 실패 없이 NOT_ELIGIBLE로 반환한다")
    void createPartiallySucceedsForActiveMembershipsOnly() {
        Event event = event(1L);
        GroupSpace activeGroup = groupSpace(10L);
        when(eventQueryService.listShareableEvents(100L, List.of(1L))).thenReturn(List.of(event));
        when(membershipQueryService.listActiveMemberships(100L, List.of(10L, 20L)))
                .thenReturn(List.of(member(activeGroup)));
        when(shareQueryService.listExistingShares(List.of(1L), List.of(10L, 20L))).thenReturn(List.of());
        when(shareCommandService.createIfAbsent(any())).thenReturn(true);

        CreateEventGroupSharesResponse response = service.create(100L, new CreateEventGroupSharesRequest(
                List.of(1L, 1L), List.of(10L, 20L, 20L), true
        ));

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().targets())
                .extracting(target -> target.groupSpaceId() + ":" + target.status())
                .containsExactly("10:SHARED", "20:NOT_ELIGIBLE");
        verify(shareCommandService).createIfAbsent(any());
    }

    @Test
    @DisplayName("소유하지 않았거나 없는 일정은 대상 처리 전에 전체 요청을 거절한다")
    void createRejectsInvalidSourceBeforeTargetProcessing() {
        Event ownedEvent = event(1L);
        when(eventQueryService.listShareableEvents(100L, List.of(1L, 2L))).thenReturn(List.of(ownedEvent));

        assertThatThrownBy(() -> service.create(100L, new CreateEventGroupSharesRequest(
                List.of(1L, 2L), List.of(10L), false
        ))).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_FOUND)
        );
        verifyNoInteractions(membershipQueryService, shareQueryService, shareCommandService);
    }

    @Test
    @DisplayName("기존 mapping과 동시 중복 insert는 모두 ALREADY_SHARED로 반환한다")
    void createTreatsExistingAndConcurrentMappingsAsIdempotentSuccess() {
        Event event = event(1L);
        GroupSpace firstGroup = groupSpace(10L);
        GroupSpace secondGroup = groupSpace(20L);
        when(eventQueryService.listShareableEvents(100L, List.of(1L))).thenReturn(List.of(event));
        when(membershipQueryService.listActiveMemberships(100L, List.of(10L, 20L)))
                .thenReturn(List.of(member(firstGroup), member(secondGroup)));
        when(shareQueryService.listExistingShares(List.of(1L), List.of(10L, 20L))).thenReturn(List.of(
                com.calio.calendar.sharing.event.domain.PersonalEventGroupShare.create(event, firstGroup, false)
        ));
        when(shareCommandService.createIfAbsent(any())).thenReturn(false);

        CreateEventGroupSharesResponse response = service.create(100L, new CreateEventGroupSharesRequest(
                List.of(1L), List.of(10L, 20L), false
        ));

        assertThat(response.results().getFirst().targets())
                .extracting(target -> target.status())
                .containsExactly(GroupShareTargetStatus.ALREADY_SHARED, GroupShareTargetStatus.ALREADY_SHARED);
    }

    @Test
    @DisplayName("원본 소유자는 대상별 공유 상태를 조회한다")
    void listReturnsTargetSpecificShareStatesForSourceOwner() {
        Event event = event(1L);
        GroupSpace groupSpace = groupSpace(10L);
        var share = com.calio.calendar.sharing.event.domain.PersonalEventGroupShare.create(
                event,
                groupSpace,
                true
        );
        when(eventQueryService.getEventForShareManagement(100L, 1L)).thenReturn(event);
        when(shareQueryService.listByEventId(1L)).thenReturn(List.of(share));

        List<GroupShareStatusResponse> response = service.list(100L, 1L);

        assertThat(response).singleElement().satisfies(status -> {
            assertThat(status.groupSpaceId()).isEqualTo(10L);
            assertThat(status.groupSpaceName()).isEqualTo("group");
            assertThat(status.isAnonymous()).isTrue();
            assertThat(status.publicShareId()).isEqualTo(share.getPublicShareId());
        });
        verifyNoInteractions(shareCommandService);
    }

    @Test
    @DisplayName("원본 소유자는 한 대상의 익명 여부만 변경한다")
    void changeAnonymousUpdatesOnlyRequestedTargetMapping() {
        Event event = event(1L);
        GroupSpace firstGroup = groupSpace(10L);
        GroupSpace secondGroup = groupSpace(20L);
        var firstShare = com.calio.calendar.sharing.event.domain.PersonalEventGroupShare.create(
                event, firstGroup, false
        );
        var secondShare = com.calio.calendar.sharing.event.domain.PersonalEventGroupShare.create(
                event, secondGroup, false
        );
        when(eventQueryService.getEventForShareManagement(100L, 1L)).thenReturn(event);
        when(shareQueryService.getByEventIdAndGroupSpaceId(1L, 10L)).thenReturn(firstShare);
        doAnswer(invocation -> {
            invocation.<com.calio.calendar.sharing.event.domain.PersonalEventGroupShare>getArgument(0)
                    .changeAnonymous(invocation.getArgument(1));
            return null;
        }).when(shareCommandService).changeAnonymous(firstShare, true);

        GroupShareStatusResponse response = service.changeAnonymous(
                100L, 1L, 10L, new UpdateGroupShareAnonymousRequest(true)
        );

        assertThat(response.isAnonymous()).isTrue();
        assertThat(firstShare.isAnonymous()).isTrue();
        assertThat(secondShare.isAnonymous()).isFalse();
        verify(shareCommandService).changeAnonymous(firstShare, true);
    }

    @Test
    @DisplayName("원본 소유자는 지정한 대상 mapping만 해제한다")
    void removeDeletesOnlyRequestedTargetMapping() {
        Event event = event(1L);
        var requestedShare = com.calio.calendar.sharing.event.domain.PersonalEventGroupShare.create(
                event, groupSpace(10L), false
        );
        var remainingShare = com.calio.calendar.sharing.event.domain.PersonalEventGroupShare.create(
                event, groupSpace(20L), false
        );
        when(eventQueryService.getEventForShareManagement(100L, 1L)).thenReturn(event);
        when(shareQueryService.getByEventIdAndGroupSpaceId(1L, 10L)).thenReturn(requestedShare);

        service.remove(100L, 1L, 10L);

        verify(shareCommandService).delete(requestedShare);
        verify(shareCommandService, never()).delete(remainingShare);
    }

    private Event event(Long id) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(id);
        return event;
    }

    private GroupSpace groupSpace(Long id) {
        GroupSpace groupSpace = new GroupSpace(100L, "group", null);
        ReflectionTestUtils.setField(groupSpace, "id", id);
        return groupSpace;
    }

    private GroupMember member(GroupSpace groupSpace) {
        return new GroupMember(groupSpace, 100L, "member", java.time.Instant.parse("2026-08-01T00:00:00Z"));
    }
}
