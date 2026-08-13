package com.calio.calendar.groupinvitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class GroupInvitationQueryServiceTest {

    @Mock
    private GroupInvitationRepository invitationRepository;

    @Mock
    private GroupSpaceRepository groupSpaceRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private GroupInvitationQueryService queryService;

    @Test
    @DisplayName("QueryService의 모든 조회는 readOnly 트랜잭션 경계 안에서 실행한다")
    void queryServiceUsesReadOnlyTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                GroupInvitationQueryService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    @DisplayName("초대장 잠금 조회는 CODE 자격 증명을 repository의 INVITE_CODE 계약으로 변환한다")
    void getInvitationForUpdateMapsCodeCredentialType() {
        // given
        byte[] credentialHash = new byte[32];
        GroupInvitation invitation = invitation();
        when(invitationRepository.findByIdAndCredentialHashForUpdate(
                10L,
                "INVITE_CODE",
                credentialHash
        )).thenReturn(Optional.of(invitation));

        // when
        GroupInvitation result = queryService.getInvitationForUpdate(
                10L,
                InvitationCredentialType.CODE,
                credentialHash
        );

        // then
        assertThat(result).isSameAs(invitation);
        verify(invitationRepository).findByIdAndCredentialHashForUpdate(
                10L,
                "INVITE_CODE",
                credentialHash
        );
    }

    private GroupInvitation invitation() {
        return new GroupInvitation(
                20L,
                30L,
                new byte[32],
                new byte[32],
                Instant.parse("2026-08-15T00:00:00Z")
        );
    }
}
