package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.controller.dto.UpdateGroupSpaceRequest;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-space-concurrency-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(GroupSpaceConcurrencyTest.ConcurrencyTestConfig.class)
class GroupSpaceConcurrencyTest {

    @Autowired
    private GroupSpaceService groupSpaceService;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BlockingDeletionCleanup blockingCleanup;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        groupMemberRepository.deleteAllInBatch();
        groupSpaceRepository.deleteAllInBatch();
        blockingCleanup.reset();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        blockingCleanup.releaseDelete();
        executor.shutdownNow();
    }

    @Test
    @DisplayName("DELETE가 group row lock을 보유하면 PATCH는 대기하고 삭제 commit 뒤 GROUP_SPACE_NOT_FOUND가 된다")
    void givenConcurrentDeleteAndPatch_whenDeleteCommitsFirst_thenPatchReturnsNotFound() throws Exception {
        Aggregate aggregate = createAggregate();
        Future<?> delete = executor.submit(() ->
                groupSpaceService.delete(aggregate.ownerAccountId(), aggregate.groupSpaceId())
        );
        assertThat(blockingCleanup.awaitDeleteLock(Duration.ofSeconds(3))).isTrue();

        UpdateGroupSpaceRequest patchRequest = new UpdateGroupSpaceRequest();
        patchRequest.setName("대기 중 수정");
        CountDownLatch patchStarted = new CountDownLatch(1);
        Future<?> patch = executor.submit(() -> {
            patchStarted.countDown();
            return groupSpaceService.patch(
                    aggregate.ownerAccountId(),
                    aggregate.groupSpaceId(),
                    patchRequest
            );
        });
        assertThat(patchStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(patch.isDone()).isFalse();

        blockingCleanup.releaseDelete();
        delete.get(3, TimeUnit.SECONDS);

        assertThatThrownByFuture(patch);
    }

    private Aggregate createAggregate() {
        Account owner = accountRepository.saveAndFlush(new Account());
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace("동시성", null, owner.getId())
        );
        groupMemberRepository.saveAndFlush(
                new GroupMember(groupSpace.getId(), owner.getId(), "owner")
        );
        return new Aggregate(groupSpace.getId(), owner.getId());
    }

    private void assertThatThrownByFuture(Future<?> patch) throws Exception {
        try {
            patch.get(3, TimeUnit.SECONDS);
            throw new AssertionError("PATCH가 GROUP_SPACE_NOT_FOUND 없이 완료됐다.");
        } catch (ExecutionException exception) {
            assertThat(exception.getCause())
                    .isInstanceOfSatisfying(CalioException.class, calioException ->
                            assertThat(calioException.getErrorCode())
                                    .isEqualTo(ErrorCode.GROUP_SPACE_NOT_FOUND)
                    );
        }
    }

    private record Aggregate(Long groupSpaceId, Long ownerAccountId) {
    }

    @TestConfiguration
    static class ConcurrencyTestConfig {

        @Bean
        BlockingDeletionCleanup blockingDeletionCleanup() {
            return new BlockingDeletionCleanup();
        }
    }

    static class BlockingDeletionCleanup implements GroupSpaceDeletionCleanup {

        private CountDownLatch deleteLocked;
        private CountDownLatch releaseDelete;

        void reset() {
            deleteLocked = new CountDownLatch(1);
            releaseDelete = new CountDownLatch(1);
        }

        @Override
        public void deleteGroupSchedules(Long groupSpaceId) {
            deleteLocked.countDown();
            awaitRelease();
        }

        boolean awaitDeleteLock(Duration timeout) throws InterruptedException {
            return deleteLocked.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        void releaseDelete() {
            if (releaseDelete != null) {
                releaseDelete.countDown();
            }
        }

        private void awaitRelease() {
            try {
                if (!releaseDelete.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시성 테스트의 delete release를 기다리지 못했다.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("동시성 테스트 thread가 중단됐다.", exception);
            }
        }
    }
}
