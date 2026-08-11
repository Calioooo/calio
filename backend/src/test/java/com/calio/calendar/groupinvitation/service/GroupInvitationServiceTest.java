package com.calio.calendar.groupinvitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.controller.dto.IssueGroupInvitationResponse;
import com.calio.calendar.groupinvitation.controller.dto.PreviewGroupInvitationRequest;
import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.calio.calendar.groupinvitation.service.dto.InvitationCredentialPair;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.domain.GroupMember;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupInvitationServiceTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final Long GROUP_SPACE_ID = 20L;
    private static final Long MEMBER_ID = 30L;
    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");

    @Mock
    private InvitationCredentialService credentialService;

    @Mock
    private GroupInvitationQueryService queryService;

    @Mock
    private GroupInvitationCommandService commandService;

    private GroupInvitationService service;

    @BeforeEach
    void setUp() {
        GroupInvitationProperties properties = new GroupInvitationProperties();
        service = new GroupInvitationService(
                credentialService,
                queryService,
                commandService,
                new NoOpTransactionManager(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties
        );
        GroupSpace groupSpace = new GroupSpace(ACCOUNT_ID, "Calio", "📅");
        ReflectionTestUtils.setField(groupSpace, "id", GROUP_SPACE_ID);
        GroupMember member = new GroupMember(groupSpace, ACCOUNT_ID, "member", NOW);
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        lenient().when(queryService.getGroupSpaceForUpdate(GROUP_SPACE_ID)).thenReturn(groupSpace);
        lenient().when(queryService.getActiveMemberForUpdate(GROUP_SPACE_ID, ACCOUNT_ID)).thenReturn(member);
    }

    @Test
    @DisplayName("초대 미리보기는 credential을 해싱하고 유효한 초대의 그룹과 active member 수를 조합한다")
    void previewCombinesValidatedInvitationQueries() {
        // given
        byte[] credentialHash = new byte[32];
        PreviewGroupInvitationRequest request = new PreviewGroupInvitationRequest(
                InvitationCredentialType.LINK_TOKEN,
                "A".repeat(43)
        );
        GroupInvitation invitation = new GroupInvitation(
                GROUP_SPACE_ID,
                30L,
                new byte[32],
                new byte[32],
                NOW.plusSeconds(3600)
        );
        GroupSpace groupSpace = new GroupSpace(ACCOUNT_ID, "Calio", "📅");
        org.springframework.test.util.ReflectionTestUtils.setField(groupSpace, "id", GROUP_SPACE_ID);
        when(credentialService.hashValidated(request.credentialType(), request.credential()))
                .thenReturn(credentialHash);
        when(queryService.getInvitationByCredentialHash(request.credentialType(), credentialHash))
                .thenReturn(invitation);
        when(queryService.getGroupSpace(GROUP_SPACE_ID)).thenReturn(groupSpace);
        when(queryService.getActiveMemberCount(GROUP_SPACE_ID)).thenReturn(4);

        // when
        var response = service.preview(request);

        // then
        assertThat(response.name()).isEqualTo("Calio");
        assertThat(response.emoji()).isEqualTo("📅");
        assertThat(response.memberCount()).isEqualTo(4);
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    @DisplayName("초대장 목록은 Query의 domain 결과를 summary 응답으로 변환한다")
    void listMapsQueryResultsToSummaryResponse() {
        // given
        GroupInvitation invitation = new GroupInvitation(
                GROUP_SPACE_ID,
                30L,
                new byte[32],
                new byte[32],
                Instant.parse("2026-07-29T08:00:00Z")
        );
        when(queryService.list(ACCOUNT_ID, GROUP_SPACE_ID)).thenReturn(java.util.List.of(invitation));

        // when
        var response = service.list(ACCOUNT_ID, GROUP_SPACE_ID);

        // then
        assertThat(response.invitations()).hasSize(1);
        assertThat(response.invitations().getFirst().expiresAt())
                .isEqualTo(Instant.parse("2026-07-29T08:00:00Z"));
        verify(queryService).list(ACCOUNT_ID, GROUP_SPACE_ID);
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
        when(commandService.create(GROUP_SPACE_ID, MEMBER_ID, first, NOW.plus(properties().getTtl())))
                .thenThrow(credentialCollision("uk_group_invitations_link_token_hash"));
        when(commandService.create(GROUP_SPACE_ID, MEMBER_ID, second, NOW.plus(properties().getTtl())))
                .thenThrow(credentialCollision("uk_group_invitations_invite_code_hash"));
        when(commandService.create(GROUP_SPACE_ID, MEMBER_ID, third, NOW.plus(properties().getTtl())))
                .thenReturn(expected);

        // when
        var response = service.issue(ACCOUNT_ID, GROUP_SPACE_ID);

        // then
        assertThat(response.invitationId()).isEqualTo(99L);
        assertThat(response.inviteCode()).isEqualTo(third.inviteCode());
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-07-29T08:00:00Z"));
        verify(credentialService, times(3)).generatePair();
        verify(commandService, times(3)).create(
                eq(GROUP_SPACE_ID),
                eq(MEMBER_ID),
                any(InvitationCredentialPair.class),
                eq(NOW.plus(properties().getTtl()))
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
        when(commandService.create(GROUP_SPACE_ID, MEMBER_ID, first, NOW.plus(properties().getTtl())))
                .thenThrow(credentialCollision("uk_group_invitations_link_token_hash"));
        when(commandService.create(GROUP_SPACE_ID, MEMBER_ID, second, NOW.plus(properties().getTtl())))
                .thenThrow(credentialCollision("uk_group_invitations_link_token_hash"));
        when(commandService.create(GROUP_SPACE_ID, MEMBER_ID, third, NOW.plus(properties().getTtl())))
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
    @DisplayName("credential unique constraint와 무관한 무결성 오류는 재시도하지 않고 issue failure로 변환한다")
    void mapsUnrelatedDataIntegrityViolationToIssueFailure() {
        // given
        InvitationCredentialPair credentials = pair("A");
        DataIntegrityViolationException expected =
                new DataIntegrityViolationException("foreign key constraint violation");
        when(credentialService.generatePair()).thenReturn(credentials);
        when(commandService.create(GROUP_SPACE_ID, MEMBER_ID, credentials, NOW.plus(properties().getTtl())))
                .thenThrow(expected);

        // when, then
        assertThatThrownBy(() -> service.issue(ACCOUNT_ID, GROUP_SPACE_ID))
                .isInstanceOfSatisfying(CalioException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.GROUP_INVITATION_ISSUE_FAILED);
                    assertThat(exception.getCause()).isSameAs(expected);
                });
        verify(credentialService).generatePair();
        verify(commandService).create(
                GROUP_SPACE_ID,
                MEMBER_ID,
                credentials,
                NOW.plus(properties().getTtl())
        );
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

    private GroupInvitationProperties properties() {
        return new GroupInvitationProperties();
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
