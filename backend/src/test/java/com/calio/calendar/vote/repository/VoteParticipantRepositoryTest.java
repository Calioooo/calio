package com.calio.calendar.vote.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantStatus;
import com.calio.calendar.vote.domain.VoteRoom;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vote-participant-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class VoteParticipantRepositoryTest {

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private VoteParticipantRepository voteParticipantRepository;

    @Autowired
    private VoteRoomRepository voteRoomRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntityManager entityManager;

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
    @Transactional
    @DisplayName("참여자는 VoteRoom 공개 ID와 대소문자 무관 닉네임으로 VoteRoom을 함께 조회한다")
    void givenParticipant_whenFindByVoteRoomPublicIdAndNickname_thenLoadsVoteRoom() {
        // given
        VoteParticipant participant = voteParticipantRepository.saveAndFlush(
                new VoteParticipant(voteRoom, "calio", null)
        );

        // when
        VoteParticipant foundParticipant = voteParticipantRepository
                .findByVoteRoomPublicIdAndNickname(voteRoom.getPublicId(), "CALIO")
                .orElseThrow();

        // then
        assertThat(foundParticipant.getId()).isEqualTo(participant.getId());
        assertThat(foundParticipant.getVoteRoom().getId()).isEqualTo(voteRoom.getId());
        assertThat(foundParticipant.getPasswordHash()).isNull();
        assertThat(foundParticipant.getStatus()).isEqualTo(VoteParticipantStatus.REGISTERED);
    }

    @Test
    @Transactional
    @DisplayName("참여자가 투표를 제출하면 SUBMITTED 상태로 저장된다")
    void givenRegisteredParticipant_whenSubmit_thenPersistsSubmittedStatus() {
        // given
        VoteParticipant participant = voteParticipantRepository.saveAndFlush(
                new VoteParticipant(voteRoom, "calio", null)
        );

        // when
        participant.submit();
        voteParticipantRepository.flush();
        entityManager.clear();

        // then
        VoteParticipant foundParticipant = voteParticipantRepository.findById(participant.getId()).orElseThrow();
        assertThat(foundParticipant.getStatus()).isEqualTo(VoteParticipantStatus.SUBMITTED);
    }

    @Test
    @DisplayName("같은 VoteRoom에서는 같은 닉네임의 참여자를 저장할 수 없다")
    void givenDuplicateNicknameInVoteRoom_whenSave_thenRejectsUniqueConstraint() {
        // given
        voteParticipantRepository.saveAndFlush(new VoteParticipant(voteRoom, "calio", null));

        // when, then
        assertThatThrownBy(() -> voteParticipantRepository.saveAndFlush(
                new VoteParticipant(voteRoom, "calio", "hashed-password")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 참여자는 같은 불가능 날짜를 중복 투표할 수 없다")
    void givenDuplicateUnavailableDateForParticipant_whenSave_thenRejectsUniqueConstraint() {
        // given
        VoteParticipant participant = voteParticipantRepository.saveAndFlush(
                new VoteParticipant(voteRoom, "calio", null)
        );
        LocalDate unavailableDate = LocalDate.of(2026, 8, 15);
        voteRepository.saveAndFlush(new Vote(participant, unavailableDate));

        // when, then
        assertThatThrownBy(() -> voteRepository.saveAndFlush(new Vote(participant, unavailableDate)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("참여자의 날짜 Vote 조회는 unavailableDate 오름차순으로 반환한다")
    void givenVotesForParticipant_whenFindAllByVoteParticipantId_thenSortsByUnavailableDate() {
        // given
        VoteParticipant participant = voteParticipantRepository.saveAndFlush(
                new VoteParticipant(voteRoom, "calio", "hashed-password")
        );
        voteRepository.saveAllAndFlush(List.of(
                new Vote(participant, LocalDate.of(2026, 8, 20)),
                new Vote(participant, LocalDate.of(2026, 8, 15))
        ));

        // when, then
        assertThat(voteRepository.findAllByVoteParticipantId(participant.getId()))
                .extracting(Vote::getUnavailableDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 8, 20)
                );
    }

    @Test
    @DisplayName("제출한 참여자의 Vote만 날짜별 집계와 닉네임 projection으로 조회한다")
    void givenSubmittedAndRegisteredParticipants_whenFindResultProjections_thenExcludesRegisteredParticipant() {
        VoteParticipant submittedParticipant = voteParticipantRepository.saveAndFlush(
                new VoteParticipant(voteRoom, "submitted", null)
        );
        submittedParticipant.submit();
        voteParticipantRepository.saveAndFlush(submittedParticipant);
        VoteParticipant registeredParticipant = voteParticipantRepository.saveAndFlush(
                new VoteParticipant(voteRoom, "register", null)
        );
        LocalDate selectedDate = LocalDate.of(2026, 8, 15);
        voteRepository.saveAllAndFlush(List.of(
                new Vote(submittedParticipant, selectedDate),
                new Vote(registeredParticipant, selectedDate)
        ));

        assertThat(voteRepository.findAllSubmittedByVoteRoomPublicId(voteRoom.getPublicId()))
                .extracting(Vote::getUnavailableDate)
                .containsExactly(selectedDate);
        assertThat(voteRepository.findAllSubmittedByVoteRoomPublicId(voteRoom.getPublicId()))
                .extracting(vote -> vote.getVoteParticipant().getNickname())
                .containsExactly("submitted");
        assertThat(voteParticipantRepository.findSubmittedNicknamesByVoteRoomPublicId(voteRoom.getPublicId()))
                .containsExactly("submitted");
    }
}
