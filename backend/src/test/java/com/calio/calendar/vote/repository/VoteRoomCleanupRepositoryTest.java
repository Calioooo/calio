package com.calio.calendar.vote.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.vote.domain.VoteRoom;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vote-room-cleanup-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class VoteRoomCleanupRepositoryTest {

    @Autowired private VoteRoomRepository voteRoomRepository;
    @Autowired private AccountRepository accountRepository;

    private Account account;

    @BeforeEach
    void setUp() {
        voteRoomRepository.deleteAll();
        accountRepository.deleteAll();
        account = accountRepository.saveAndFlush(new Account());
    }

    @Test
    @Transactional
    @DisplayName("후보 종료일 뒤 90일 보존 경계일은 유지하고 이전 VoteRoom만 삭제한다")
    void givenRetentionBoundary_whenDeleteExpiredVoteRooms_thenDeletesOnlyOlderVoteRooms() {
        LocalDate cutoffDate = LocalDate.of(2026, 6, 1);
        VoteRoom expiredVoteRoom = voteRoomRepository.saveAndFlush(voteRoom("만료", cutoffDate.minusDays(1)));
        VoteRoom boundaryVoteRoom = voteRoomRepository.saveAndFlush(voteRoom("경계", cutoffDate));
        VoteRoom activeVoteRoom = voteRoomRepository.saveAndFlush(voteRoom("유지", cutoffDate.plusDays(1)));

        int deletedCount = voteRoomRepository.deleteExpiredVoteRoomsBefore(cutoffDate);

        assertThat(deletedCount).isOne();
        assertThat(voteRoomRepository.findById(expiredVoteRoom.getId())).isEmpty();
        assertThat(voteRoomRepository.findById(boundaryVoteRoom.getId())).isPresent();
        assertThat(voteRoomRepository.findById(activeVoteRoom.getId())).isPresent();
    }

    private VoteRoom voteRoom(String name, LocalDate candidateEndDate) {
        return new VoteRoom(
                UUID.randomUUID(),
                name,
                candidateEndDate.minusDays(6),
                candidateEndDate,
                account
        );
    }
}
