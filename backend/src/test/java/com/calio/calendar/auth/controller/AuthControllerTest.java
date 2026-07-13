package com.calio.calendar.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.auth.service.AccessTokenEncoder;
import com.calio.calendar.account.repository.AccountAuthTokenRepository;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.account.domain.AccountAuthToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenEncoder accessTokenEncoder;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountAuthTokenRepository accountAuthTokenRepository;

    @BeforeEach
    void setUp() {
        accountAuthTokenRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("게스트 인증 발급은 인증 없이 Account와 tokenHash만 저장하고 raw token을 한 번만 반환한다")
    void givenNoAuthentication_whenCreateGuestToken_thenReturnsRawTokenAndPersistsOnlyHash() throws Exception {
        // when
        MvcResult result = mockMvc.perform(post("/api/auth/guest"))
                // then
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String rawToken = response.get("accessToken").asString();
        AccountAuthToken persistedToken = accountAuthTokenRepository.findAll().getFirst();

        assertThat(rawToken).isNotBlank();
        assertThat(rawToken.length()).isGreaterThanOrEqualTo(43);
        assertThat(rawToken).matches("[A-Za-z0-9_-]+");
        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(accountAuthTokenRepository.count()).isEqualTo(1);
        assertThat(persistedToken.getTokenHash()).isEqualTo(accessTokenEncoder.hash(rawToken));
        assertThat(persistedToken.getTokenHash()).isNotEqualTo(rawToken);
    }
}
