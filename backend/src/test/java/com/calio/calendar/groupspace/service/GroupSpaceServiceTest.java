package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.repository.AccountRepository;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupSpaceServiceTest {

    private static final long GROUP_SPACE_ID = 1L;
    private static final long OWNER_ACCOUNT_ID = 10L;

    @Mock
    private GroupSpaceRepository groupSpaceRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private GroupSpaceInputNormalizer inputNormalizer;

    @Mock
    private GroupSpaceDeletionCleanup deletionCleanup;

    private GroupSpaceService service;
    private GroupSpace groupSpace;
    private GroupMember ownerMembership;

    @BeforeEach
    void setUp() {
        service = new GroupSpaceService(
                groupSpaceRepository,
                groupMemberRepository,
                accountRepository,
                inputNormalizer,
                List.of(deletionCleanup)
        );
        groupSpace = new GroupSpace("그룹", null, OWNER_ACCOUNT_ID);
        ownerMembership = new GroupMember(GROUP_SPACE_ID, OWNER_ACCOUNT_ID, "owner");
        when(groupSpaceRepository.findByIdForUpdate(GROUP_SPACE_ID)).thenReturn(Optional.of(groupSpace));
        when(groupMemberRepository.findAllByGroupSpaceIdForUpdate(GROUP_SPACE_ID))
                .thenReturn(List.of(ownerMembership));
    }

    @Test
    @DisplayName("DELETE는 GroupSpace와 GroupMember를 잠근 뒤 dependent cleanup과 hard-delete를 순서대로 수행한다")
    void givenOwner_whenDelete_thenUsesCanonicalLockAndCleanupOrder() {
        service.delete(OWNER_ACCOUNT_ID, GROUP_SPACE_ID);

        InOrder order = inOrder(groupSpaceRepository, groupMemberRepository, deletionCleanup);
        order.verify(groupSpaceRepository).findByIdForUpdate(GROUP_SPACE_ID);
        order.verify(groupMemberRepository).findAllByGroupSpaceIdForUpdate(GROUP_SPACE_ID);
        order.verify(deletionCleanup).deleteGroupSchedules(GROUP_SPACE_ID);
        order.verify(deletionCleanup).deleteGroupInvitations(GROUP_SPACE_ID);
        order.verify(groupMemberRepository).deleteAllInBatch(List.of(ownerMembership));
        order.verify(groupMemberRepository).flush();
        order.verify(groupSpaceRepository).delete(groupSpace);
        order.verify(groupSpaceRepository).flush();
    }

    @Test
    @DisplayName("dependent cleanup이 실패하면 membership과 GroupSpace 삭제를 시작하지 않는다")
    void givenCleanupFailure_whenDelete_thenDoesNotDeleteAggregateRows() {
        RuntimeException cleanupFailure = new RuntimeException("cleanup failed");
        org.mockito.Mockito.doThrow(cleanupFailure)
                .when(deletionCleanup)
                .deleteGroupInvitations(GROUP_SPACE_ID);

        assertThatThrownBy(() -> service.delete(OWNER_ACCOUNT_ID, GROUP_SPACE_ID))
                .isSameAs(cleanupFailure);

        verify(groupMemberRepository, never()).deleteAllInBatch(List.of(ownerMembership));
        verify(groupSpaceRepository, never()).delete(groupSpace);
    }
}
