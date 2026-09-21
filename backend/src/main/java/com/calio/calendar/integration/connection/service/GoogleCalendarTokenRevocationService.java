package com.calio.calendar.integration.connection.service;

import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.security.TokenEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarTokenRevocationService {

  private static final Logger log =
      LoggerFactory.getLogger(GoogleCalendarTokenRevocationService.class);

  private final GoogleOAuthClient oauthClient;
  private final TokenEncryptor encryptor;

  public GoogleCalendarTokenRevocationService(
      GoogleOAuthClient oauthClient, TokenEncryptor encryptor) {
    this.oauthClient = oauthClient;
    this.encryptor = encryptor;
  }

  public void revokeSafely(Long accountId, String encryptedRefreshToken) {
    if (encryptedRefreshToken == null) {
      return;
    }
    try {
      oauthClient.revokeToken(encryptor.decrypt(encryptedRefreshToken));
    } catch (RuntimeException exception) {
      log.warn(
          "Google Calendar token revocation failed after local credential removal. accountId={}",
          accountId,
          exception);
    }
  }
}
