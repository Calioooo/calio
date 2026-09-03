package com.calio.calendar.vote.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vote-result-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class VoteResultControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private VoteRepository voteRepository;
    @Autowired private VoteParticipantRepository voteParticipantRepository;
    @Autowired private VoteRoomRepository voteRoomRepository;
    @Autowired private AccountRepository accountRepository;

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
                "여행",
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 16),
                account
        ));
    }

    @Test
    @DisplayName("공개 결과는 후보 날짜 전체와 제출 완료 참여자 정보만 반환한다")
    void givenSubmittedAndRegisteredParticipants_whenGetPublicResult_thenReturnsSafeCurrentResult() throws Exception {
        VoteParticipant submittedParticipant = voteParticipantRepository.saveAndFlush(
                new VoteParticipant(voteRoom, "calio", "hashed-secret")
        );
        submittedParticipant.submit();
        voteParticipantRepository.saveAndFlush(submittedParticipant);
        VoteParticipant registeredParticipant = voteParticipantRepository.saveAndFlush(
                new VoteParticipant(voteRoom, "guest", "other-secret")
        );
        voteRepository.saveAllAndFlush(List.of(
                new Vote(submittedParticipant, LocalDate.of(2026, 8, 15)),
                new Vote(registeredParticipant, LocalDate.of(2026, 8, 15))
        ));

        mockMvc.perform(get("/api/vote-rooms/{publicId}", voteRoom.getPublicId()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(voteRoom.getPublicId().toString()))
                .andExpect(jsonPath("$.name").value("여행"))
                .andExpect(jsonPath("$.candidateStartDate").value("2026-08-14"))
                .andExpect(jsonPath("$.candidateEndDate").value("2026-08-16"))
                .andExpect(jsonPath("$.dates", hasSize(3)))
                .andExpect(jsonPath("$.dates[0].date").value("2026-08-14"))
                .andExpect(jsonPath("$.dates[0].unavailableCount").value(0))
                .andExpect(jsonPath("$.dates[0].unavailableNicknames", hasSize(0)))
                .andExpect(jsonPath("$.dates[1].date").value("2026-08-15"))
                .andExpect(jsonPath("$.dates[1].unavailableCount").value(1))
                .andExpect(jsonPath("$.dates[1].unavailableNicknames").value("calio"))
                .andExpect(jsonPath("$.submittedNicknames", hasSize(1)))
                .andExpect(jsonPath("$.submittedNicknames[0]").value("calio"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.createdByAccountId").doesNotExist())
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.recommendation").doesNotExist())
                .andExpect(jsonPath("$.recommendedDates").doesNotExist());
    }

    @Test
    @DisplayName("캐시 무효화 쿼리가 포함된 공개 결과 조회는 익명으로 성공한다")
    void givenCacheBustQuery_whenAnonymousGetsPublicResult_thenReturnsOk() throws Exception {
        mockMvc.perform(get("/api/vote-rooms/{publicId}", voteRoom.getPublicId())
                        .queryParam("cacheBust", "1")
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(voteRoom.getPublicId().toString()));
    }

    @Test
    @DisplayName("없는 공개 VoteRoom 결과는 not found 오류를 반환한다")
    void givenMissingVoteRoom_whenGetPublicResult_thenReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/vote-rooms/{publicId}", UUID.randomUUID()).with(anonymous()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("VOTE_ROOM_NOT_FOUND"));
    }
}
