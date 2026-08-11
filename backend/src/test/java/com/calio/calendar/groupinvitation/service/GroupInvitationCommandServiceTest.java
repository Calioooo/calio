package com.calio.calendar.groupinvitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupinvitation.service.dto.InvitationCredentialPair;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class GroupInvitationCommandServiceTest {

    @Mock
    private GroupInvitationRepository invitationRepository;

    @InjectMocks
    private GroupInvitationCommandService commandService;

    @Test
    @DisplayName("CommandService의 모든 상태 변경은 트랜잭션 경계 안에서 실행한다")
    void commandServiceUsesTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                GroupInvitationCommandService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    @DisplayName("초대장 생성 command는 전달받은 정보로 domain을 저장하고 저장 결과를 반환한다")
    void createReturnsSavedInvitationDomain() {
        // given
        InvitationCredentialPair credentials = new InvitationCredentialPair(
                "A".repeat(43),
                "AAAA-BBBB-CCCC-DDDD",
                new byte[32],
                new byte[32]
        );
        Instant expiresAt = Instant.parse("2026-07-29T08:00:00Z");
        when(invitationRepository.saveAndFlush(any(GroupInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        GroupInvitation result = commandService.create(20L, 30L, credentials, expiresAt);

        // then
        ArgumentCaptor<GroupInvitation> invitation = ArgumentCaptor.forClass(GroupInvitation.class);
        verify(invitationRepository).saveAndFlush(invitation.capture());
        assertThat(result).isSameAs(invitation.getValue());
        assertThat(result.getGroupSpaceId()).isEqualTo(20L);
        assertThat(result.getCreatedByMemberId()).isEqualTo(30L);
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
    }
}
