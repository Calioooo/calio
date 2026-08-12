package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupSpaceLockCommandServiceTest {

    @Mock
    private GroupSpaceRepository groupSpaceRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    private GroupSpaceCommandService groupSpaceCommandService;
    private GroupMembershipCommandService membershipCommandService;

    @BeforeEach
    void setUp() {
        groupSpaceCommandService = new GroupSpaceCommandService(
                groupSpaceRepository,
                groupMemberRepository
        );
        membershipCommandService = new GroupMembershipCommandService(
                groupMemberRepository,
                groupSpaceRepository
        );
    }

    @Test
    @DisplayName("GroupSpace command 잠금 조회는 ID를 repository에 그대로 위임한다")
    void groupSpaceCommandLockDelegatesGroupSpaceId() {
        GroupSpace groupSpace = org.mockito.Mockito.mock(GroupSpace.class);
        when(groupSpaceRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(groupSpace));

        GroupSpace result = groupSpaceCommandService.lockGroupSpace(20L);

        assertThat(result).isSameAs(groupSpace);
        verify(groupSpaceRepository).findByIdForUpdate(20L);
    }

    @Test
    @DisplayName("GroupMembership command 잠금 조회는 그룹의 멤버 목록을 그대로 반환한다")
    void membershipCommandLockMembersReturnsRepositoryResult() {
        List<GroupMember> members = List.of(org.mockito.Mockito.mock(GroupMember.class));
        when(groupMemberRepository.findAllByGroupSpaceIdForUpdateOrderById(20L))
                .thenReturn(members);

        List<GroupMember> result = membershipCommandService.lockMembers(20L);

        assertThat(result).isSameAs(members);
        verify(groupMemberRepository).findAllByGroupSpaceIdForUpdateOrderById(20L);
    }

    @Test
    @DisplayName("초대 수락용 그룹 잠금 조회가 실패하면 GROUP_INVITATION_NOT_FOUND를 반환한다")
    void invitationScopedGroupLockMapsMissingGroupToInvitationNotFound() {
        when(groupSpaceRepository.findByIdForUpdate(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipCommandService.lockGroupSpaceForInvitation(20L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GROUP_INVITATION_NOT_FOUND)
                );
    }
}
