package com.calio.calendar.vote.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.testsupport.SharedIntegrationDatabase;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:participated-vote-room-controller-test;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
@SharedIntegrationDatabase
class ParticipatedVoteRoomControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private VoteParticipantRepository voteParticipantRepository;
  @Autowired private VoteRoomRepository voteRoomRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  private Long authenticatedAccountId;

  @BeforeEach
  void setUp() {
    authenticatedAccountId = currentAccountId();
    voteParticipantRepository.deleteAll();
    voteRoomRepository.deleteAll();
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("내가 참여한 투표 목록은 accountId가 일치하는 REGISTERED와 SUBMITTED 참여자를 수정순으로 반환한다")
  void
      givenParticipants_whenListParticipated_thenReturnsOnlyCurrentAccountsParticipantsInUpdatedOrder()
          throws Exception {
    VoteRoom registeredVoteRoom = voteRoom("생성하고 참여한 투표", authenticatedAccountId);
    VoteRoom submittedVoteRoom = voteRoom("제출한 투표", 999L);
    VoteRoom guestVoteRoom = voteRoom("비사용자 참여", 999L);
    VoteRoom otherAccountVoteRoom = voteRoom("다른 사용자 참여", 999L);
    voteRoomRepository.saveAllAndFlush(
        java.util.List.of(
            registeredVoteRoom, submittedVoteRoom, guestVoteRoom, otherAccountVoteRoom));

    VoteParticipant registeredParticipant =
        voteParticipantRepository.saveAndFlush(
            VoteParticipant.forAccount(
                registeredVoteRoom.getId(), "register", authenticatedAccountId));
    VoteParticipant submittedParticipant =
        voteParticipantRepository.saveAndFlush(
            VoteParticipant.forAccount(
                submittedVoteRoom.getId(), "submitted", authenticatedAccountId));
    submittedParticipant.submit();
    voteParticipantRepository.saveAndFlush(submittedParticipant);
    voteParticipantRepository.saveAndFlush(
        new VoteParticipant(guestVoteRoom.getId(), "guest", null));
    voteParticipantRepository.saveAndFlush(
        VoteParticipant.forAccount(otherAccountVoteRoom.getId(), "other", 2L));
    updateParticipantUpdatedAt(registeredParticipant, "2026-08-15T00:00:00Z");
    updateParticipantUpdatedAt(submittedParticipant, "2026-08-16T00:00:00Z");
    entityManager.clear();

    mockMvc
        .perform(get("/api/vote-rooms/me/participated"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].publicId").value(submittedVoteRoom.getPublicId().toString()))
        .andExpect(jsonPath("$[0].nickname").value("submitted"))
        .andExpect(jsonPath("$[0].participantStatus").value("SUBMITTED"))
        .andExpect(jsonPath("$[0].participantUpdatedAt").value("2026-08-16T00:00:00Z"))
        .andExpect(jsonPath("$[1].publicId").value(registeredVoteRoom.getPublicId().toString()))
        .andExpect(jsonPath("$[1].nickname").value("register"))
        .andExpect(jsonPath("$[1].participantStatus").value("REGISTERED"))
        .andExpect(jsonPath("$[1].participantUpdatedAt").value("2026-08-15T00:00:00Z"))
        .andExpect(jsonPath("$[0].accountId").doesNotExist())
        .andExpect(jsonPath("$[0].password").doesNotExist())
        .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$[0].*", hasSize(7)));

    mockMvc
        .perform(get("/api/vote-rooms/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].publicId").value(registeredVoteRoom.getPublicId().toString()));
  }

  @Test
  @DisplayName("인증 없는 요청은 내가 참여한 투표 목록을 조회할 수 없다")
  void unauthenticatedRequestCannotListParticipatedVoteRooms() throws Exception {
    mockMvc
        .perform(get("/api/vote-rooms/me/participated").with(anonymous()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_REQUIRED"));
  }

  @Test
  @DisplayName("참여한 투표가 없으면 빈 목록을 반환한다")
  void givenNoParticipatedVoteRooms_whenListParticipated_thenReturnsEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/vote-rooms/me/participated"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  private VoteRoom voteRoom(String name, Long createdByAccountId) {
    return new VoteRoom(
        UUID.randomUUID(),
        name,
        LocalDate.of(2026, 8, 14),
        LocalDate.of(2026, 8, 20),
        createdByAccountId);
  }

  private void updateParticipantUpdatedAt(VoteParticipant participant, String updatedAt) {
    jdbcTemplate.update(
        "UPDATE vote_participants SET updated_at = ? WHERE id = ?",
        Timestamp.from(Instant.parse(updatedAt)),
        participant.getId());
  }
}
