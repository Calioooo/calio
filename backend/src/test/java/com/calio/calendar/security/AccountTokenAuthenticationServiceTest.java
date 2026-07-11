package com.calio.calendar.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.auth.AccessTokenEncoder;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.AccountAuthTokenRepository;
import com.calio.calendar.repository.AccountRepository;
import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.AccountAuthToken;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-token-authentication-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountTokenAuthenticationServiceTest {

    @Autowired
    private AccountTokenAuthenticationService authenticationService;

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
    @DisplayName("유효한 Bearer token은 accountId만 가진 principal을 만들고 lastUsedAt을 갱신한다")
    void givenValidToken_whenAuthenticate_thenReturnsAccountIdOnlyPrincipalAndUpdatesLastUsedAt() {
        // given
        String rawToken = "valid-token";
        Account account = accountRepository.saveAndFlush(new Account());
        AccountAuthToken authToken = accountAuthTokenRepository.saveAndFlush(
                new AccountAuthToken(account, accessTokenEncoder.hash(rawToken))
        );
        Instant beforeAuthentication = Instant.now();

        // when
        AuthenticatedAccount principal = authenticationService.authenticate(rawToken);

        // then
        AccountAuthToken updatedToken = accountAuthTokenRepository.findById(authToken.getId()).orElseThrow();
        assertThat(principal.accountId()).isEqualTo(account.getId());
        assertThat(updatedToken.getLastUsedAt()).isNotNull();
        assertThat(updatedToken.getLastUsedAt()).isAfterOrEqualTo(beforeAuthentication.minusSeconds(1));
    }

    @Test
    @DisplayName("저장된 tokenHash가 없으면 AUTH_TOKEN_INVALID로 거부한다")
    void givenUnknownToken_whenAuthenticate_thenThrowsInvalidToken() {
        // when, then
        assertThatThrownBy(() -> authenticationService.authenticate("missing-token"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID)
                );
    }

    @Test
    @DisplayName("revokedAt이 있는 token은 AUTH_TOKEN_REVOKED로 거부한다")
    void givenRevokedToken_whenAuthenticate_thenThrowsRevokedToken() {
        // given
        String rawToken = "revoked-token";
        Account account = accountRepository.saveAndFlush(new Account());
        AccountAuthToken authToken = new AccountAuthToken(account, accessTokenEncoder.hash(rawToken));
        authToken.revoke(Instant.parse("2026-07-10T00:00:00Z"));
        accountAuthTokenRepository.saveAndFlush(authToken);

        // when, then
        assertThatThrownBy(() -> authenticationService.authenticate(rawToken))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_REVOKED)
                );
    }
}
