package com.calio.calendar.groupinvitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.groupinvitation.controller.dto.IssueGroupInvitationResponse;
import com.calio.calendar.groupinvitation.service.dto.InvitationCredentialPair;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class GroupInvitationServiceTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final Long GROUP_SPACE_ID = 20L;

    @Mock
    private InvitationCredentialService credentialService;

    @Mock
    private GroupInvitationCommandService commandService;

    private GroupInvitationService service;

    @BeforeEach
    void setUp() {
        service = new GroupInvitationService(
                credentialService,
                commandService,
                new NoOpTransactionManager()
        );
    }

    @Test
    @DisplayName("unique collision은 매 시도 전체를 rollback하고 새 credential pair로 최대 3회 재시도한다")
    void retriesUniqueCollisionWithFreshCredentials() {
        // given
        InvitationCredentialPair first = pair("A");
        InvitationCredentialPair second = pair("B");
        InvitationCredentialPair third = pair("C");
        when(credentialService.generatePair()).thenReturn(first, second, third);
        IssueGroupInvitationResponse expected = new IssueGroupInvitationResponse(
                99L,
                "https://calio.app/invite/" + third.linkToken(),
                third.inviteCode(),
                Instant.parse("2026-07-29T08:00:00Z")
        );
        when(commandService.issueOnce(ACCOUNT_ID, GROUP_SPACE_ID, first))
                .thenThrow(credentialCollision("uk_group_invitations_link_token_hash"));
        when(commandService.issueOnce(ACCOUNT_ID, GROUP_SPACE_ID, second))
                .thenThrow(credentialCollision("uk_group_invitations_invite_code_hash"));
        when(commandService.issueOnce(ACCOUNT_ID, GROUP_SPACE_ID, third)).thenReturn(expected);

        // when
        var response = service.issue(ACCOUNT_ID, GROUP_SPACE_ID);

        // then
        assertThat(response.invitationId()).isEqualTo(99L);
        assertThat(response.inviteCode()).isEqualTo(third.inviteCode());
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-07-29T08:00:00Z"));
        verify(credentialService, times(3)).generatePair();
        verify(commandService, times(3)).issueOnce(
                eq(ACCOUNT_ID),
                eq(GROUP_SPACE_ID),
                any(InvitationCredentialPair.class)
        );
    }

    @Test
    @DisplayName("세 번의 collision 뒤에는 credential을 노출하지 않는 generation failure로 종료한다")
    void failsAfterThreeCollisions() {
        // given
        InvitationCredentialPair first = pair("A");
        InvitationCredentialPair second = pair("B");
        InvitationCredentialPair third = pair("C");
        when(credentialService.generatePair()).thenReturn(first, second, third);
        when(commandService.issueOnce(ACCOUNT_ID, GROUP_SPACE_ID, first))
                .thenThrow(credentialCollision("uk_group_invitations_link_token_hash"));
        when(commandService.issueOnce(ACCOUNT_ID, GROUP_SPACE_ID, second))
                .thenThrow(credentialCollision("uk_group_invitations_link_token_hash"));
        when(commandService.issueOnce(ACCOUNT_ID, GROUP_SPACE_ID, third))
                .thenThrow(credentialCollision("uk_group_invitations_link_token_hash"));

        // when, then
        assertThatThrownBy(() -> service.issue(ACCOUNT_ID, GROUP_SPACE_ID))
                .isInstanceOfSatisfying(CalioException.class, exception -> {
                    assertThat(exception.getErrorCode().name())
                            .isEqualTo("GROUP_INVITATION_GENERATION_FAILED");
                    assertThat(exception.getMessage()).doesNotContain("token");
                });
        verify(credentialService, times(3)).generatePair();
    }

    @Test
    @DisplayName("credential unique constraint와 무관한 무결성 오류는 재시도하거나 변환하지 않는다")
    void propagatesUnrelatedDataIntegrityViolation() {
        // given
        InvitationCredentialPair credentials = pair("A");
        DataIntegrityViolationException expected =
                new DataIntegrityViolationException("foreign key constraint violation");
        when(credentialService.generatePair()).thenReturn(credentials);
        when(commandService.issueOnce(ACCOUNT_ID, GROUP_SPACE_ID, credentials))
                .thenThrow(expected);

        // when, then
        assertThatThrownBy(() -> service.issue(ACCOUNT_ID, GROUP_SPACE_ID)).isSameAs(expected);
        verify(credentialService).generatePair();
        verify(commandService).issueOnce(ACCOUNT_ID, GROUP_SPACE_ID, credentials);
    }

    private DataIntegrityViolationException credentialCollision(String constraintName) {
        return new DataIntegrityViolationException(
                "insert failed",
                new IllegalStateException("Duplicate entry for key '" + constraintName + "'")
        );
    }

    private InvitationCredentialPair pair(String seed) {
        return new InvitationCredentialPair(
                seed.repeat(43),
                seed.repeat(4) + "-" + seed.repeat(4) + "-" + seed.repeat(4) + "-" + seed.repeat(4),
                new byte[32],
                new byte[32]
        );
    }

    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
