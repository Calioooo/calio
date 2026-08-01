package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.service.GroupInvitationService;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class GroupSpaceServiceConstraintTest {

    private GroupMemberRepository groupMemberRepository;
    private GroupSpaceService groupSpaceService;

    @BeforeEach
    void setUp() {
        GroupSpaceRepository groupSpaceRepository = mock(GroupSpaceRepository.class);
        groupMemberRepository = mock(GroupMemberRepository.class);
        AccountRepository accountRepository = mock(AccountRepository.class);
        groupSpaceService = new GroupSpaceService(
                groupSpaceRepository,
                groupMemberRepository,
                accountRepository,
                mock(GroupInvitationService.class),
                new NoOpGroupScheduleShareCleanupAdapter(),
                Clock.systemUTC()
        );

        when(accountRepository.existsById(1L)).thenReturn(true);
        when(groupSpaceRepository.saveAndFlush(any(GroupSpace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("ACTIVE nickname constraint 충돌은 원인을 보존한 GROUP_MEMBER_NICKNAME_CONFLICT로 변환한다")
    void activeNicknameConstraintIsMappedToStableErrorCode() {
        // given
        DataIntegrityViolationException integrityViolation = new DataIntegrityViolationException(
                "Duplicate entry for key 'uk_group_member_active_nickname'"
        );
        when(groupMemberRepository.saveAndFlush(any()))
                .thenThrow(integrityViolation);

        // when, then
        assertThatThrownBy(() -> groupSpaceService.create(
                1L,
                new CreateGroupSpaceRequest("Group", null, "owner")
        ))
                .isInstanceOf(CalioException.class)
                .hasCause(integrityViolation)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_MEMBER_NICKNAME_CONFLICT);
    }

    @Test
    @DisplayName("다른 integrity constraint 오류는 nickname 충돌로 변환하지 않는다")
    void unrelatedConstraintIsNotMappedToNicknameConflict() {
        // given
        DataIntegrityViolationException integrityViolation = new DataIntegrityViolationException(
                "Foreign key constraint 'fk_group_members_account'"
        );
        when(groupMemberRepository.saveAndFlush(any())).thenThrow(integrityViolation);

        // when, then
        assertThatThrownBy(() -> groupSpaceService.create(
                1L,
                new CreateGroupSpaceRequest("Group", null, "owner")
        )).isSameAs(integrityViolation);
    }
}
