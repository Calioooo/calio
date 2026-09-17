package com.calio.calendar.integration.sync.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@DiscriminatorValue("EVENT")
public class GoogleCalendarEventJob extends GoogleOperationJob {

  @Enumerated(EnumType.STRING)
  @Column(name = "event_operation_kind", updatable = false, length = 64)
  private GoogleCalendarEventJobKind kind;

  @Column(name = "event_id", updatable = false)
  private Long eventId;

  @Column(name = "provider_identity", updatable = false, length = 1024)
  private String providerIdentity;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "target_payload", updatable = false, columnDefinition = "JSON")
  private GoogleEventJobPayload targetPayload;

  protected GoogleCalendarEventJob() {}

  public static GoogleCalendarEventJob create(
      String operationId,
      Long integrationId,
      Long accountId,
      long integrationSequence,
      GoogleCalendarEventJobKind kind,
      Long eventId,
      String providerIdentity,
      GoogleEventJobPayload targetPayload,
      Instant runnableAt) {
    if (kind == null
        || eventId == null
        || (kind == GoogleCalendarEventJobKind.DELETE && targetPayload != null)
        || (kind != GoogleCalendarEventJobKind.DELETE && targetPayload == null)) {
      throw new IllegalArgumentException("Google Event job fields are required");
    }
    if (kind == GoogleCalendarEventJobKind.CREATE && !hasText(providerIdentity)) {
      throw new IllegalArgumentException("Google Event CREATE job requires provider identity");
    }
    GoogleCalendarEventJob job = new GoogleCalendarEventJob();
    job.initialize(operationId, integrationId, accountId, integrationSequence, runnableAt);
    job.kind = kind;
    job.eventId = eventId;
    job.providerIdentity = providerIdentity;
    job.targetPayload = targetPayload;
    return job;
  }

  public GoogleCalendarEventJobKind getKind() {
    return kind;
  }

  public Long getEventId() {
    return eventId;
  }

  public String getProviderIdentity() {
    return providerIdentity;
  }

  public GoogleEventJobPayload getTargetPayload() {
    return targetPayload;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
