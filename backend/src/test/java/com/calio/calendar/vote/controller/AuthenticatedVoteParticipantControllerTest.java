package com.calio.calendar.vote.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.testsupport.SharedIntegrationDatabase;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:calendar-authenticated-participant-controller-test;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
@SharedIntegrationDatabase
class AuthenticatedVoteParticipantControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private VoteRepository voteRepository;
  @Autowired private VoteParticipantRepository participantRepository;
  @Autowired private VoteRoomRepository voteRoomRepository;
  @Autowired private AccountRepository accountRepository;

  private VoteRoom voteRoom;
  private Long authenticatedAccountId;

  @BeforeEach
  void setUp() {
    authenticatedAccountId = currentAccountId();
    voteRepository.deleteAll();
    participantRepository.deleteAll();
    voteRoomRepository.deleteAll();
    accountRepository.deleteAll();
    Account creator = accountRepository.saveAndFlush(new Account());
    voteRoom =
        voteRoomRepository.saveAndFlush(
            new VoteRoom(
                UUID.randomUUID(),
                "여행",
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 20),
                creator.getId()));
  }

  @Test
  @DisplayName("인증 사용자는 비밀번호 없이 참여자를 만들고 기존 선택을 조회 및 수정한다")
  void authenticatedParticipantCreatesLooksUpAndSubmitsVotesWithoutPassword() throws Exception {
    mockMvc
        .perform(
            post("/api/vote-rooms/{publicId}/participants/me", voteRoom.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"calio\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nickname").value("calio"))
        .andExpect(jsonPath("$.status").value("REGISTERED"))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.accountId").doesNotExist());

    VoteParticipant participant =
        participantRepository
            .findByVoteRoomPublicIdAndAccountId(voteRoom.getPublicId(), authenticatedAccountId)
            .orElseThrow();
    assertThat(participant.getPasswordHash()).isNull();

    mockMvc
        .perform(get("/api/vote-rooms/{publicId}/participants/me", voteRoom.getPublicId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REGISTERED"))
        .andExpect(jsonPath("$.unavailableDates", hasSize(0)))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.accountId").doesNotExist());

    mockMvc
        .perform(
            put("/api/vote-rooms/{publicId}/participants/me/votes", voteRoom.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"unavailableDates\":[\"2026-08-17\",\"2026-08-15\",\"2026-08-17\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.unavailableDates", hasSize(2)))
        .andExpect(jsonPath("$.unavailableDates[0]").value("2026-08-15"))
        .andExpect(jsonPath("$.unavailableDates[1]").value("2026-08-17"))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.accountId").doesNotExist());

    mockMvc
        .perform(
            put("/api/vote-rooms/{publicId}/participants/me/votes", voteRoom.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"unavailableDates\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.unavailableDates", hasSize(0)));
    assertThat(voteRepository.count()).isZero();
  }

  @Test
  @DisplayName("인증 사용자는 같은 투표방에 두 번째 참여자를 만들 수 없다")
  void authenticatedParticipantCannotCreateDuplicateAccountParticipant() throws Exception {
    participantRepository.saveAndFlush(
        VoteParticipant.forAccount(voteRoom.getId(), "first", authenticatedAccountId));

    mockMvc
        .perform(
            post("/api/vote-rooms/{publicId}/participants/me", voteRoom.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"second\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("VOTE_PARTICIPANT_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("인증 없는 요청은 인증 사용자 참여 API를 사용할 수 없다")
  void unauthenticatedRequestCannotUseAuthenticatedParticipantEndpoints() throws Exception {
    mockMvc
        .perform(
            post("/api/vote-rooms/{publicId}/participants/me", voteRoom.getPublicId())
                .with(anonymous())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"calio\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_REQUIRED"));

    mockMvc
        .perform(
            get("/api/vote-rooms/{publicId}/participants/me", voteRoom.getPublicId())
                .with(anonymous()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_REQUIRED"));

    mockMvc
        .perform(
            put("/api/vote-rooms/{publicId}/participants/me/votes", voteRoom.getPublicId())
                .with(anonymous())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"unavailableDates\":[]}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_REQUIRED"));
  }
}
