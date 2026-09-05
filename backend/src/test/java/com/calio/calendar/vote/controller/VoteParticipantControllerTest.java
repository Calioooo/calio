package com.calio.calendar.vote.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vote-participant-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
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
        voteRoom = voteRoomRepository.saveAndFlush(new VoteRoom(
                UUID.randomUUID(), "여행", LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 20), account));
    }

    @Test
    @DisplayName("비인증 공개 요청으로 참여자를 만들고 날짜 집합을 교체하면 SUBMITTED가 된다")
    void publicCreateAndReplaceVotes() throws Exception {
        mockMvc.perform(post("/api/vote-rooms/{publicId}/participants", voteRoom.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"calio\",\"password\":\"secret\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("calio"))
                .andExpect(jsonPath("$.status").value("REGISTERED"));

        mockMvc.perform(put("/api/vote-rooms/{publicId}/votes", voteRoom.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"calio\",\"password\":\"secret\",\"unavailableDates\":[\"2026-08-17\",\"2026-08-15\",\"2026-08-17\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.unavailableDates", hasSize(2)))
                .andExpect(jsonPath("$.unavailableDates[0]").value("2026-08-15"))
                .andExpect(jsonPath("$.unavailableDates[1]").value("2026-08-17"));

        mockMvc.perform(put("/api/vote-rooms/{publicId}/votes", voteRoom.getPublicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"calio\",\"password\":\"secret\",\"unavailableDates\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.unavailableDates", hasSize(0)));
        org.assertj.core.api.Assertions.assertThat(voteRepository.count()).isZero();
    }

    @Test
    @DisplayName("없는 참여자와 틀린 비밀번호는 같은 공개 자격증명 오류를 반환한다")
    void invalidCredentialUsesSameResponse() throws Exception {
        participantRepository.saveAndFlush(new VoteParticipant(
                voteRoom, "calio", new BCryptPasswordEncoder().encode("secret")));
        String wrongPassword = "{\"nickname\":\"calio\",\"password\":\"wrong\",\"unavailableDates\":[]}";
        String missingName = "{\"nickname\":\"other\",\"password\":\"secret\",\"unavailableDates\":[]}";
        for (String request : java.util.List.of(wrongPassword, missingName)) {
            mockMvc.perform(put("/api/vote-rooms/{publicId}/votes", voteRoom.getPublicId())
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("VOTE_PARTICIPANT_CREDENTIAL_INVALID"));
        }
    }
}
