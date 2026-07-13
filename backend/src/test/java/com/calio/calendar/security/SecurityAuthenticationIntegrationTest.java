package com.calio.calendar.security;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.auth.AccessTokenEncoder;
import com.calio.calendar.repository.AccountAuthTokenRepository;
import com.calio.calendar.repository.AccountRepository;
import com.calio.calendar.repository.TaskRepository;
import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.AccountAuthToken;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security-authentication-integration-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(SecurityAuthenticationIntegrationTest.SecurityTestController.class)
class SecurityAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenEncoder accessTokenEncoder;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountAuthTokenRepository accountAuthTokenRepository;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        accountAuthTokenRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("잘못된 Bearer token은 ProblemDetail 구조로 AUTH_TOKEN_INVALID를 반환한다")
    void givenInvalidBearerToken_whenRequestWithAuthorizationHeader_thenReturnsInvalidTokenProblemDetail()
            throws Exception {
        // when
        mockMvc.perform(get("/api/security-test/authenticated-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                // then
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.detail").value("Authentication token is invalid."))
                .andExpect(jsonPath("$.instance").value("/api/security-test/authenticated-account"))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("revoked Bearer token은 ProblemDetail 구조로 AUTH_TOKEN_REVOKED를 반환한다")
    void givenRevokedBearerToken_whenRequestWithAuthorizationHeader_thenReturnsRevokedTokenProblemDetail()
            throws Exception {
        // given
        String rawToken = "revoked-token";
        Account account = accountRepository.saveAndFlush(new Account());
        AccountAuthToken authToken = new AccountAuthToken(account, accessTokenEncoder.hash(rawToken));
        authToken.revoke(Instant.parse("2026-07-10T00:00:00Z"));
        accountAuthTokenRepository.saveAndFlush(authToken);

        // when
        mockMvc.perform(get("/api/security-test/authenticated-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken))
                // then
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("AUTH_TOKEN_REVOKED"))
                .andExpect(jsonPath("$.detail").value("Authentication token is revoked."))
                .andExpect(jsonPath("$.instance").value("/api/security-test/authenticated-account"))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("유효한 Bearer token은 SecurityContext에 Account entity가 아닌 accountId principal을 설정한다")
    void givenValidBearerToken_whenRequestWithAuthorizationHeader_thenControllerReceivesAccountIdPrincipal()
            throws Exception {
        // given
        String rawToken = "valid-token";
        Account account = accountRepository.saveAndFlush(new Account());
        accountAuthTokenRepository.saveAndFlush(new AccountAuthToken(account, accessTokenEncoder.hash(rawToken)));

        // when
        mockMvc.perform(get("/api/tasks/security-test/authenticated-account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId()))
                .andExpect(jsonPath("$.principalType").value("AuthenticatedAccount"));
    }

    @Test
    @DisplayName("보호 대상 domain API는 Authorization header가 없으면 AUTH_TOKEN_REQUIRED를 반환한다")
    void givenNoAuthorizationHeader_whenRequestProtectedDomainApi_thenReturnsRequiredTokenError()
            throws Exception {
        // when, then
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("AUTH_TOKEN_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Authentication token is required."))
                .andExpect(jsonPath("$.instance").value("/api/tasks"))
                .andExpect(jsonPath("$.*", hasSize(6)));
    }

    @Test
    @DisplayName("명시된 public API는 Authorization header 없이도 인증 요구 응답으로 바뀌지 않는다")
    void givenNoAuthorizationHeader_whenRequestPublicApis_thenDoesNotRequireAuthentication()
            throws Exception {
        // when, then
        mockMvc.perform(get("/api/national-holidays"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @RestController
    static class SecurityTestController {

        @GetMapping("/api/tasks/security-test/authenticated-account")
        Map<String, Object> getAuthenticatedAccount(Authentication authentication) {
            AuthenticatedAccount principal = (AuthenticatedAccount) authentication.getPrincipal();
            return Map.of(
                    "accountId", principal.accountId(),
                    "principalType", principal.getClass().getSimpleName()
            );
        }
    }
}
