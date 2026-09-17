package com.calio.calendar.vote.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vote-participant-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class VoteParticipantServiceIntegrationTest {

    @Autowired
    private VoteParticipantService voteParticipantService;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private VoteParticipantRepository voteParticipantRepository;

    @Autowired
    private VoteRoomRepository voteRoomRepository;

    @Autowired
    private AccountRepository accountRepository;

    private VoteRoom voteRoom;

    @BeforeEach
    void setUp() {
        voteRepository.deleteAll();
        voteParticipantRepository.deleteAll();
        voteRoomRepository.deleteAll();
        accountRepository.deleteAll();

        Account account = accountRepository.saveAndFlush(new Account());
        voteRoom = voteRoomRepository.saveAndFlush(new VoteRoom(
                UUID.randomUUID(),
                "여행 일정",
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 20),
                account
        ));
    }

    @Test
    @DisplayName("대소문자만 다른 동시 참여자 생성 요청은 하나만 저장되고 나머지는 닉네임 충돌이 된다")
    void givenConcurrentCaseInsensitiveNicknameRequests_whenCreate_thenCreatesOnlyOneParticipant() throws Exception {
        // given
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<CreateResult> lowerCaseResult = executorService.submit(
                    () -> createParticipantAfterStart(ready, start, "calio")
            );
            Future<CreateResult> upperCaseResult = executorService.submit(
                    () -> createParticipantAfterStart(ready, start, "CALIO")
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            start.countDown();
            List<CreateResult> results = List.of(
                    lowerCaseResult.get(5, TimeUnit.SECONDS),
                    upperCaseResult.get(5, TimeUnit.SECONDS)
            );

            // then
            assertThat(results).extracting(CreateResult::created).containsExactlyInAnyOrder(true, false);
            assertThat(results).filteredOn(result -> !result.created())
                    .extracting(CreateResult::errorCode)
                    .containsExactly(ErrorCode.VOTE_PARTICIPANT_NICKNAME_CONFLICT);
            assertThat(voteParticipantRepository.findAll()).hasSize(1);
        } finally {
            executorService.shutdownNow();
        }
    }

    private CreateResult createParticipantAfterStart(CountDownLatch ready, CountDownLatch start, String nickname)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            voteParticipantService.create(voteRoom.getPublicId(), nickname, null);
            return new CreateResult(true, null);
        } catch (CalioException exception) {
            return new CreateResult(false, exception.getErrorCode());
        }
    }

    private record CreateResult(boolean created, ErrorCode errorCode) {
    }
}
