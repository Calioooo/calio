package com.calio.calendar.integration.connection.service;

import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GoogleCalendarConnectionFailureService {

  private final GoogleCalendarConnectionCommandService connectionCommandService;
  private final GoogleCalendarTokenRevocationService tokenRevocationService;
  private final TransactionTemplate syncErrorTransaction;

  public GoogleCalendarConnectionFailureService(
      GoogleCalendarConnectionCommandService connectionCommandService,
      GoogleCalendarTokenRevocationService tokenRevocationService,
      PlatformTransactionManager transactionManager) {
    this.connectionCommandService = connectionCommandService;
    this.tokenRevocationService = tokenRevocationService;
    syncErrorTransaction = new TransactionTemplate(transactionManager);
  }

  public void pauseForReconnect(Long accountId, String reason, Instant occurredAt) {
    String encryptedRefreshToken =
        syncErrorTransaction.execute(
            status ->
                connectionCommandService
                    .tryLockConnectedConnection(accountId)
                    .map(
                        connection ->
                            markSyncErrorAndReturnRefreshToken(connection, reason, occurredAt))
                    .orElse(null));
    tokenRevocationService.revokeSafely(accountId, encryptedRefreshToken);
  }

  private String markSyncErrorAndReturnRefreshToken(
      GoogleCalendarConnection connection, String reason, Instant occurredAt) {
    String encryptedRefreshToken = connection.getEncryptedRefreshToken();
    connectionCommandService.markSyncError(connection, reason, occurredAt);
    return encryptedRefreshToken;
  }
}
