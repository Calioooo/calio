package com.calio.calendar.groupinvitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupinvitation.service.dto.InvitationCredentialPair;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Test
    @DisplayName("credential unique constraint 충돌은 재시도 가능한 credential 충돌로 변환한다")
    void mapsCredentialConstraintViolationToRetryableCollision() {
        // given
        DataIntegrityViolationException cause = credentialCollision(
                "UK_GROUP_INVITATIONS_INVITE_CODE_HASH"
        );
        when(invitationRepository.saveAndFlush(any(GroupInvitation.class))).thenThrow(cause);

        // when, then
        assertThatThrownBy(() -> commandService.create(20L, 30L, credentials(), expiresAt()))
                .isInstanceOfSatisfying(
                        CalioException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.GROUP_INVITATION_CREDENTIAL_COLLISION);
                            assertThat(exception.getCause()).isSameAs(cause);
                        }
                );
    }

    @Test
    @DisplayName("credential constraint와 무관한 무결성 오류는 원본 예외를 그대로 전파한다")
    void propagatesUnrelatedDataIntegrityViolation() {
        // given
        DataIntegrityViolationException cause =
                new DataIntegrityViolationException("foreign key constraint violation");
        when(invitationRepository.saveAndFlush(any(GroupInvitation.class))).thenThrow(cause);

        // when, then
        assertThatThrownBy(() -> commandService.create(20L, 30L, credentials(), expiresAt()))
                .isSameAs(cause);
    }

    @Test
    @DisplayName("member가 발급한 초대장 삭제 command는 잠금 조회 후 일괄 삭제한다")
    void deleteAllByCreatedByMemberIdDeletesLockedInvitations() {
        // given
        GroupInvitation invitation = new GroupInvitation(
                20L,
                30L,
                new byte[32],
                new byte[32],
                Instant.parse("2026-08-15T00:00:00Z")
        );
        List<GroupInvitation> invitations = List.of(invitation);
        when(invitationRepository.findAllByCreatedByMemberIdForUpdateOrderById(30L))
                .thenReturn(invitations);

        // when
        commandService.deleteAllByCreatedByMemberId(30L);

        // then
        verify(invitationRepository).findAllByCreatedByMemberIdForUpdateOrderById(30L);
        verify(invitationRepository).deleteAllInBatch(invitations);
    }

    @Test
    @DisplayName("초대 잠금 조회 command는 credential 인수를 repository에 그대로 위임한다")
    void lockInvitationDelegatesExactCredentialArguments() {
        // given
        byte[] credentialHash = new byte[32];
        GroupInvitation invitation = org.mockito.Mockito.mock(GroupInvitation.class);
        when(invitationRepository.findByIdAndCredentialHashForUpdate(
                40L,
                "LINK_TOKEN",
                credentialHash
        )).thenReturn(Optional.of(invitation));

        // when
        GroupInvitation result = commandService.lockInvitation(
                40L,
                InvitationCredentialType.LINK_TOKEN,
                credentialHash
        );

        // then
        assertThat(result).isSameAs(invitation);
        verify(invitationRepository).findByIdAndCredentialHashForUpdate(
                40L,
                "LINK_TOKEN",
                credentialHash
        );
    }

    @Test
    @DisplayName("초대 잠금 조회 command는 CODE 자격 증명을 repository의 INVITE_CODE 계약으로 변환한다")
    void lockInvitationMapsCodeCredentialType() {
        // given
        byte[] credentialHash = new byte[32];
        GroupInvitation invitation = org.mockito.Mockito.mock(GroupInvitation.class);
        when(invitationRepository.findByIdAndCredentialHashForUpdate(
                40L,
                "INVITE_CODE",
                credentialHash
        )).thenReturn(Optional.of(invitation));

        // when
        GroupInvitation result = commandService.lockInvitation(
                40L,
                InvitationCredentialType.CODE,
                credentialHash
        );

        // then
        assertThat(result).isSameAs(invitation);
        verify(invitationRepository).findByIdAndCredentialHashForUpdate(
                40L,
                "INVITE_CODE",
                credentialHash
        );
    }

    @Test
    @DisplayName("초대 잠금 조회 command는 대상을 찾지 못하면 초대 없음 오류로 실패한다")
    void lockInvitationRejectsMissingInvitation() {
        // given
        byte[] credentialHash = new byte[32];
        when(invitationRepository.findByIdAndCredentialHashForUpdate(40L, "LINK_TOKEN", credentialHash))
                .thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> commandService.lockInvitation(
                40L,
                InvitationCredentialType.LINK_TOKEN,
                credentialHash
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GROUP_INVITATION_NOT_FOUND)
        );
    }

    private DataIntegrityViolationException credentialCollision(String constraintName) {
        return new DataIntegrityViolationException(
                "insert failed",
                new IllegalStateException("Duplicate entry for key '" + constraintName + "'")
        );
    }

    private InvitationCredentialPair credentials() {
        return new InvitationCredentialPair(
                "A".repeat(43),
                "AAAA-BBBB-CCCC-DDDD",
                new byte[32],
                new byte[32]
        );
    }

    private Instant expiresAt() {
        return Instant.parse("2026-07-29T08:00:00Z");
    }
}
