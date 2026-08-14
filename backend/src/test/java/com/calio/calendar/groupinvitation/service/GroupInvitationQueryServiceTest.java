package com.calio.calendar.groupinvitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class GroupInvitationQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");

    @Mock
    private GroupInvitationRepository invitationRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private GroupInvitationQueryService queryService;

    @Test
    @DisplayName("QueryService의 모든 조회는 readOnly 트랜잭션 경계 안에서 실행한다")
    void queryServiceUsesReadOnlyTransactionBoundary() {
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                GroupInvitationQueryService.class,
                Transactional.class
        );

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    @DisplayName("초대 목록 조회는 그룹·멤버 범위와 현재 시각을 repository에 전달한다")
    void listDelegatesInvitationScope() {
        List<GroupInvitation> invitations = List.of(invitation());
        when(clock.instant()).thenReturn(NOW);
        when(invitationRepository.findActiveInvitations(20L, 30L, NOW)).thenReturn(invitations);

        assertThat(queryService.list(20L, 30L)).isSameAs(invitations);
        verify(invitationRepository).findActiveInvitations(20L, 30L, NOW);
    }

    @Test
    @DisplayName("존재하지 않는 link 초대 조회는 초대 없음 오류로 실패한다")
    void getInvitationByCredentialHashRejectsMissingInvitation() {
        byte[] credentialHash = new byte[32];
        when(invitationRepository.findByLinkTokenHash(credentialHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getInvitationByCredentialHash(
                InvitationCredentialType.LINK_TOKEN,
                credentialHash
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GROUP_INVITATION_NOT_FOUND)
        );
    }

    @Test
    @DisplayName("만료 초대 cleanup 조회는 cutoff와 page 요청을 repository에 전달한다")
    void listExpiredBeforeDelegatesCleanupArguments() {
        Instant cutoff = NOW.minusSeconds(1);
        PageRequest pageRequest = PageRequest.of(0, 100);
        List<GroupInvitation> invitations = List.of(invitation());
        when(invitationRepository.findCleanupBatch(cutoff, pageRequest)).thenReturn(invitations);

        assertThat(queryService.listExpiredBefore(cutoff, pageRequest)).isSameAs(invitations);
        verify(invitationRepository).findCleanupBatch(cutoff, pageRequest);
    }

    private GroupInvitation invitation() {
        return new GroupInvitation(20L, 30L, new byte[32], new byte[32], NOW.plusSeconds(3600));
    }
}
