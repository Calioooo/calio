package com.calio.calendar.security.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.auth.service.AccessTokenEncoder;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.common.testsupport.SharedIntegrationDatabase;
import com.calio.calendar.security.AuthenticatedAccount;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:calendar-shared-integration-test;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@SharedIntegrationDatabase
class AuthenticateAccountTokenUseCaseTest {

  @Autowired private AuthenticateAccountTokenUseCase authenticateAccountTokenUseCase;

  @Autowired private AccessTokenEncoder accessTokenEncoder;

  @Autowired private AccountRepository accountRepository;

  @BeforeEach
  void setUp() {
    accountRepository.deleteAll();
  }

  @Test
  @DisplayName("유효한 Bearer token은 accountId만 가진 principal을 만들고 lastUsedAt을 갱신한다")
  void givenValidToken_whenAuthenticate_thenReturnsAccountIdOnlyPrincipalAndUpdatesLastUsedAt() {
    // given
    String rawToken = "valid-token";
    Account account = new Account();
    account.issueAuthToken(accessTokenEncoder.hash(rawToken));
    account = accountRepository.saveAndFlush(account);
    Instant beforeAuthentication = Instant.now();

    // when
    AuthenticatedAccount principal = authenticateAccountTokenUseCase.authenticate(rawToken);

    // then
    Account updatedAccount = accountRepository.findById(account.getId()).orElseThrow();
    assertThat(principal.accountId()).isEqualTo(account.getId());
    assertThat(updatedAccount.getAuthTokenLastUsedAt()).isNotNull();
    assertThat(updatedAccount.getAuthTokenLastUsedAt())
        .isAfterOrEqualTo(beforeAuthentication.minusSeconds(1));
  }

  @Test
  @DisplayName("저장된 tokenHash가 없으면 AUTH_TOKEN_INVALID로 거부한다")
  void givenUnknownToken_whenAuthenticate_thenThrowsInvalidToken() {
    assertThatThrownBy(() -> authenticateAccountTokenUseCase.authenticate("missing-token"))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
  }

  @Test
  @DisplayName("revokedAt이 있는 token은 AUTH_TOKEN_REVOKED로 거부한다")
  void givenRevokedToken_whenAuthenticate_thenThrowsRevokedToken() {
    // given
    String rawToken = "revoked-token";
    Account account = new Account();
    account.issueAuthToken(accessTokenEncoder.hash(rawToken));
    account.revokeAuthToken(Instant.parse("2026-07-10T00:00:00Z"));
    accountRepository.saveAndFlush(account);

    // when, then
    assertThatThrownBy(() -> authenticateAccountTokenUseCase.authenticate(rawToken))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_TOKEN_REVOKED));
  }
}
