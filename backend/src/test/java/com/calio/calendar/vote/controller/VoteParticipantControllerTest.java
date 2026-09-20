package com.calio.calendar.vote.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.testsupport.SharedIntegrationDatabase;
import com.calio.calendar.vote.domain.Vote;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:calendar-shared-public-controller-test;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc
@SharedIntegrationDatabase
class VoteParticipantControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private VoteRepository voteRepository;
  @Autowired private VoteParticipantRepository participantRepository;
  @Autowired private VoteRoomRepository voteRoomRepository;
  @Autowired private AccountRepository accountRepository;

  private VoteRoom voteRoom;

  @BeforeEach
  void setUp() {
    voteRepository.deleteAll();
    participantRepository.deleteAll();
    voteRoomRepository.deleteAll();
    accountRepository.deleteAll();
    Account account = accountRepository.saveAndFlush(new Account());
    voteRoom =
        voteRoomRepository.saveAndFlush(
            new VoteRoom(
                UUID.randomUUID(),
                "여행",
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 20),
                account.getId()));
  }

  @Test
  @DisplayName("비인증 공개 요청으로 참여자를 만들고 날짜 집합을 교체하면 SUBMITTED가 된다")
  void publicCreateAndReplaceVotes() throws Exception {
    mockMvc
        .perform(
            post("/api/vote-rooms/{publicId}/participants", voteRoom.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"calio\",\"password\":\"secret\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nickname").value("calio"))
        .andExpect(jsonPath("$.status").value("REGISTERED"));

    mockMvc
        .perform(
            put("/api/vote-rooms/{publicId}/votes", voteRoom.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nickname\":\"calio\",\"password\":\"secret\",\"unavailableDates\":[\"2026-08-17\",\"2026-08-15\",\"2026-08-17\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.unavailableDates", hasSize(2)))
        .andExpect(jsonPath("$.unavailableDates[0]").value("2026-08-15"))
        .andExpect(jsonPath("$.unavailableDates[1]").value("2026-08-17"));

    mockMvc
        .perform(
            put("/api/vote-rooms/{publicId}/votes", voteRoom.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"nickname\":\"calio\",\"password\":\"secret\",\"unavailableDates\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.unavailableDates", hasSize(0)));
    org.assertj.core.api.Assertions.assertThat(voteRepository.count()).isZero();
  }

  @Test
  @DisplayName("없는 참여자와 틀린 비밀번호는 같은 공개 자격증명 오류를 반환한다")
  void invalidCredentialUsesSameResponse() throws Exception {
    participantRepository.saveAndFlush(
        new VoteParticipant(voteRoom, "calio", new BCryptPasswordEncoder().encode("secret")));
    String wrongPassword =
        "{\"nickname\":\"calio\",\"password\":\"wrong\",\"unavailableDates\":[]}";
    String missingName = "{\"nickname\":\"other\",\"password\":\"secret\",\"unavailableDates\":[]}";
    for (String request : java.util.List.of(wrongPassword, missingName)) {
      mockMvc
          .perform(
              put("/api/vote-rooms/{publicId}/votes", voteRoom.getPublicId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(request))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.errorCode").value("VOTE_PARTICIPANT_CREDENTIAL_INVALID"));
    }
  }

  @Test
  @DisplayName("비인증 공개 요청으로 제출된 참여자의 기존 선택을 복원한다")
  void publicLookupRestoresSubmittedParticipantSelection() throws Exception {
    VoteParticipant participant =
        participantRepository.saveAndFlush(
            new VoteParticipant(voteRoom, "calio", new BCryptPasswordEncoder().encode("secret")));
    participant.submit();
    participantRepository.saveAndFlush(participant);
    voteRepository.saveAllAndFlush(
        java.util.List.of(
            new Vote(participant, LocalDate.of(2026, 8, 17)),
            new Vote(participant, LocalDate.of(2026, 8, 15))));

    mockMvc
        .perform(
            post("/api/vote-rooms/{publicId}/votes/lookup", voteRoom.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"calio\",\"password\":\"secret\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value("calio"))
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.unavailableDates", hasSize(2)))
        .andExpect(jsonPath("$.unavailableDates[0]").value("2026-08-15"))
        .andExpect(jsonPath("$.unavailableDates[1]").value("2026-08-17"))
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  @DisplayName("선택 복원은 REGISTERED 참여자에게 빈 날짜 목록을 반환한다")
  void lookupReturnsEmptyDatesForRegisteredParticipant() throws Exception {
    participantRepository.saveAndFlush(new VoteParticipant(voteRoom, "calio", null));

    mockMvc
        .perform(
            post("/api/vote-rooms/{publicId}/votes/lookup", voteRoom.getPublicId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"calio\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REGISTERED"))
        .andExpect(jsonPath("$.unavailableDates", hasSize(0)));
  }

  @Test
  @DisplayName("선택 복원의 잘못된 자격증명은 참여자 존재 여부와 무관하게 401을 반환한다")
  void lookupInvalidCredentialUsesSameResponse() throws Exception {
    participantRepository.saveAndFlush(
        new VoteParticipant(voteRoom, "calio", new BCryptPasswordEncoder().encode("secret")));
    String wrongPassword = "{\"nickname\":\"calio\",\"password\":\"wrong\"}";
    String missingName = "{\"nickname\":\"other\",\"password\":\"secret\"}";

    for (String request : java.util.List.of(wrongPassword, missingName)) {
      mockMvc
          .perform(
              post("/api/vote-rooms/{publicId}/votes/lookup", voteRoom.getPublicId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(request))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.errorCode").value("VOTE_PARTICIPANT_CREDENTIAL_INVALID"));
    }
  }

  @Test
  @DisplayName("선택 복원은 삭제되었거나 존재하지 않는 VoteRoom에 404를 반환한다")
  void lookupMissingVoteRoomReturnsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/vote-rooms/{publicId}/votes/lookup", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"calio\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("VOTE_ROOM_NOT_FOUND"));
  }
}
