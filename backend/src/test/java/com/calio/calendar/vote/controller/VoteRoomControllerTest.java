package com.calio.calendar.vote.controller;

import static com.calio.calendar.security.TestAccountSupport.currentAccountId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vote-room-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import({
        AuthenticatedAccountMockMvcTestConfig.class,
        VoteRoomControllerTest.FixedClockConfig.class
})
class VoteRoomControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    private static final LocalDate CANDIDATE_START_DATE = LocalDate.of(2026, 8, 14);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VoteRoomRepository voteRoomRepository;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        voteRoomRepository.deleteAll();
    }

    @Test
    @DisplayName("인증된 사용자는 KST 생성일을 시작일로 하는 투표방을 생성한다")
    void givenValidRequest_whenCreateVoteRoom_thenReturnsVoteRoomJsonContract() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/api/vote-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "주말 여행",
                                  "candidateEndDate": "2026-08-20"
                                }
                                """))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").isString())
                .andExpect(jsonPath("$.name").value("주말 여행"))
                .andExpect(jsonPath("$.candidateStartDate").value("2026-08-14"))
                .andExpect(jsonPath("$.candidateEndDate").value("2026-08-20"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(UUID.fromString(response.get("publicId").asString())).isNotNull();
        assertThat(voteRoomRepository.count()).isOne();
    }

    @Test
    @DisplayName("투표방 이름 또는 후보 종료일이 유효하지 않으면 VALIDATION_FAILED를 반환한다")
    void givenInvalidCreateRequest_whenCreateVoteRoom_thenReturnsValidationFailed() throws Exception {
        mockMvc.perform(post("/api/vote-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/vote-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "기간 초과",
                                  "candidateEndDate": "2026-09-14"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("인증 토큰 없이 투표방을 생성하거나 내 목록을 조회할 수 없다")
    void unauthenticatedRequestCannotCreateOrListVoteRooms() throws Exception {
        mockMvc.perform(post("/api/vote-rooms")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "인증 필요",
                                  "candidateEndDate": "2026-08-20"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_REQUIRED"));

        mockMvc.perform(get("/api/vote-rooms/me").with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_REQUIRED"));
    }

    @Test
    @DisplayName("내 투표방 목록은 생성자 본인의 투표방만 VoteRoom JSON 계약으로 반환한다")
    void givenVoteRoomsFromDifferentCreators_whenListMine_thenReturnsOnlyCurrentAccountsVoteRooms() throws Exception {
        // given
        Account currentAccount = accountRepository.getReferenceById(currentAccountId());
        Account otherAccount = accountRepository.saveAndFlush(new Account());
        VoteRoom ownVoteRoom = voteRoomRepository.saveAndFlush(voteRoom("내 투표방", currentAccount));
        voteRoomRepository.saveAndFlush(voteRoom("다른 사용자의 투표방", otherAccount));

        // when, then
        mockMvc.perform(get("/api/vote-rooms/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].publicId").value(ownVoteRoom.getPublicId().toString()))
                .andExpect(jsonPath("$[0].name").value("내 투표방"))
                .andExpect(jsonPath("$[0].candidateStartDate").value("2026-08-14"))
                .andExpect(jsonPath("$[0].candidateEndDate").value("2026-08-20"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].createdByAccountId").doesNotExist())
                .andExpect(jsonPath("$[0].*", hasSize(4)));
    }

    private VoteRoom voteRoom(String name, Account account) {
        return new VoteRoom(
                UUID.randomUUID(),
                name,
                CANDIDATE_START_DATE,
                CANDIDATE_START_DATE.plusDays(6),
                account
        );
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedVoteRoomClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
