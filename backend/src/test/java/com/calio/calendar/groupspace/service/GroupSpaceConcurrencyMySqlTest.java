package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationRequest;
import com.calio.calendar.groupspace.controller.dto.CreateGroupInvitationResponse;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.controller.dto.GroupMemberListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceResponseDto;
import com.calio.calendar.groupspace.domain.GroupJoinResult;
import com.calio.calendar.groupspace.domain.GroupMemberRole;
import com.calio.calendar.groupspace.domain.InvitationCredentialType;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class GroupSpaceConcurrencyMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
            .withDatabaseName("calendar")
            .withUsername("calendar")
            .withPassword("calendar");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private GroupSpaceService groupSpaceService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("같은 nickname의 동시 accept는 한 membership만 ACTIVE로 만들고 다른 요청은 conflict로 끝난다")
    void concurrentAcceptPreservesActiveNicknameUniqueness() throws Exception {
        Account owner = accountRepository.saveAndFlush(new Account());
        Account firstJoiner = accountRepository.saveAndFlush(new Account());
        Account secondJoiner = accountRepository.saveAndFlush(new Account());
        GroupSpaceResponseDto group = groupSpaceService.createGroup(
                owner.getId(),
                new CreateGroupSpaceRequest("Concurrency", null, "Owner")
        );
        CreateGroupInvitationResponse invitation =
                groupSpaceService.createInvitation(owner.getId(), group.id());
        CountDownLatch start = new CountDownLatch(1);

        List<Object> outcomes = runConcurrently(
                () -> acceptAfter(start, firstJoiner.getId(), invitation, "Shared"),
                () -> acceptAfter(start, secondJoiner.getId(), invitation, "shared"),
                start
        );

        assertThat(outcomes).filteredOn(GroupJoinResult.JOINED::equals).hasSize(1);
        assertThat(outcomes).filteredOn(ErrorCode.GROUP_MEMBER_NICKNAME_CONFLICT::equals).hasSize(1);
        assertThat(groupSpaceService.listMembers(owner.getId(), group.id()).members()).hasSize(2);
    }

    @Test
    @DisplayName("동시 OWNER 위임은 하나만 성공하고 group에는 파생 OWNER가 정확히 한 명 남는다")
    void concurrentOwnerTransferLeavesExactlyOneOwner() throws Exception {
        Account owner = accountRepository.saveAndFlush(new Account());
        Account firstTarget = accountRepository.saveAndFlush(new Account());
        Account secondTarget = accountRepository.saveAndFlush(new Account());
        GroupSpaceResponseDto group = groupSpaceService.createGroup(
                owner.getId(),
                new CreateGroupSpaceRequest("Transfer", null, "Owner")
        );
        CreateGroupInvitationResponse invitation =
                groupSpaceService.createInvitation(owner.getId(), group.id());
        Long firstMemberId = join(firstTarget, invitation, "First");
        Long secondMemberId = join(secondTarget, invitation, "Second");
        CountDownLatch start = new CountDownLatch(1);

        List<Object> outcomes = runConcurrently(
                () -> transferAfter(start, owner.getId(), group.id(), firstMemberId),
                () -> transferAfter(start, owner.getId(), group.id(), secondMemberId),
                start
        );

        assertThat(outcomes).filteredOn("TRANSFERRED"::equals).hasSize(1);
        assertThat(outcomes).filteredOn(ErrorCode.GROUP_OWNER_REQUIRED::equals).hasSize(1);
        GroupMemberListResponse members = groupSpaceService.listMembers(firstTarget.getId(), group.id());
        assertThat(members.members())
                .filteredOn(member -> member.role() == GroupMemberRole.OWNER)
                .hasSize(1);
    }

    private Object acceptAfter(
            CountDownLatch start,
            Long accountId,
            CreateGroupInvitationResponse invitation,
            String nickname
    ) throws InterruptedException {
        start.await();
        try {
            return groupSpaceService.acceptInvitation(
                    accountId,
                    new AcceptGroupInvitationRequest(
                            InvitationCredentialType.CODE,
                            invitation.inviteCode(),
                            nickname
                    )
            ).joinResult();
        } catch (CalioException exception) {
            return exception.getErrorCode();
        }
    }

    private Object transferAfter(
            CountDownLatch start,
            Long ownerAccountId,
            Long groupSpaceId,
            Long targetMemberId
    ) throws InterruptedException {
        start.await();
        try {
            groupSpaceService.transferOwner(ownerAccountId, groupSpaceId, targetMemberId);
            return "TRANSFERRED";
        } catch (CalioException exception) {
            return exception.getErrorCode();
        }
    }

    private Long join(
            Account account,
            CreateGroupInvitationResponse invitation,
            String nickname
    ) {
        return groupSpaceService.acceptInvitation(
                account.getId(),
                new AcceptGroupInvitationRequest(
                        InvitationCredentialType.CODE,
                        invitation.inviteCode(),
                        nickname
                )
        ).membership().memberId();
    }

    private List<Object> runConcurrently(
            CheckedTask first,
            CheckedTask second,
            CountDownLatch start
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> firstResult = executor.submit(first::run);
            Future<Object> secondResult = executor.submit(second::run);
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface CheckedTask {
        Object run() throws Exception;
    }
}
