package com.calio.calendar.groupinvitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupinvitation.service.dto.InvitationCredentialPair;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
    private GroupInvitationRepository invitationRepository;

    @Mock
    private GroupSpaceRepository groupSpaceRepository;

    @Mock
    private GroupMemberRepository memberRepository;

    @Mock
    private InvitationCredentialService credentialService;

    @Mock
    private GroupSpace groupSpace;

    @Mock
    private GroupMember member;

    private GroupInvitationService service;

    @BeforeEach
    void setUp() {
        when(groupSpaceRepository.findByIdForUpdate(GROUP_SPACE_ID))
                .thenReturn(Optional.of(groupSpace));
        when(memberRepository.findByGroupSpaceIdAndAccountIdForUpdate(GROUP_SPACE_ID, ACCOUNT_ID))
                .thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(
                com.calio.calendar.groupspace.domain.GroupMemberStatus.ACTIVE
        );
        when(member.getId()).thenReturn(30L);

        GroupInvitationProperties properties = new GroupInvitationProperties();
        service = new GroupInvitationService(
                invitationRepository,
                groupSpaceRepository,
                memberRepository,
                credentialService,
                properties,
                Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC),
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
        GroupInvitation saved = mock(GroupInvitation.class);
        when(saved.getId()).thenReturn(99L);
        when(saved.getExpiresAt()).thenReturn(Instant.parse("2026-07-29T08:00:00Z"));
        when(invitationRepository.saveAndFlush(any(GroupInvitation.class)))
                .thenThrow(new DataIntegrityViolationException("collision"))
                .thenThrow(new DataIntegrityViolationException("collision"))
                .thenReturn(saved);
        when(credentialService.inviteUrl(third.linkToken()))
                .thenReturn("https://calio.app/invite/" + third.linkToken());

        // when
        var response = service.issue(ACCOUNT_ID, GROUP_SPACE_ID);

        // then
        assertThat(response.invitationId()).isEqualTo(99L);
        assertThat(response.inviteCode()).isEqualTo(third.inviteCode());
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-07-29T08:00:00Z"));
        verify(credentialService, times(3)).generatePair();
        verify(invitationRepository, times(3)).saveAndFlush(any(GroupInvitation.class));
    }

    @Test
    @DisplayName("세 번의 collision 뒤에는 credential을 노출하지 않는 generation failure로 종료한다")
    void failsAfterThreeCollisions() {
        // given
        when(credentialService.generatePair()).thenReturn(pair("A"), pair("B"), pair("C"));
        when(invitationRepository.saveAndFlush(any(GroupInvitation.class)))
                .thenThrow(new DataIntegrityViolationException("collision"));

        // when, then
        assertThatThrownBy(() -> service.issue(ACCOUNT_ID, GROUP_SPACE_ID))
                .isInstanceOfSatisfying(CalioException.class, exception -> {
                    assertThat(exception.getErrorCode().name())
                            .isEqualTo("GROUP_INVITATION_GENERATION_FAILED");
                    assertThat(exception.getMessage()).doesNotContain("token");
                });
        verify(credentialService, times(3)).generatePair();
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
