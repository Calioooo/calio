package com.calio.calendar.integration.sync.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceJobPayload;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@DiscriminatorValue("RECURRENCE")
public class GoogleCalendarRecurrenceJob extends GoogleOperationJob {

  @Enumerated(EnumType.STRING)
  @Column(name = "recurrence_operation_kind", updatable = false, length = 64)
  private GoogleCalendarRecurrenceJobKind kind;

  @Embedded private GoogleCalendarRecurrenceJobTarget target;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "recurrence_target_payload", updatable = false, columnDefinition = "JSON")
  private GoogleRecurrenceJobPayload targetPayload;

  @Column(name = "recurrence_provider_identity", updatable = false, length = 1024)
  private String providerIdentity;

  protected GoogleCalendarRecurrenceJob() {}

  public static GoogleCalendarRecurrenceJob create(
      String operationId,
      Long integrationId,
      Long accountId,
      long integrationSequence,
      GoogleCalendarRecurrenceJobKind kind,
      Long recurrenceEventId,
      Instant originStartAt,
      GoogleRecurrenceJobPayload targetPayload,
      String providerIdentity,
      Instant runnableAt) {
    if (kind == null
        || (kind == GoogleCalendarRecurrenceJobKind.RECURRENCE_DELETE
                || kind == GoogleCalendarRecurrenceJobKind.OVERRIDE_DELETE)
            && targetPayload != null
        || (kind != GoogleCalendarRecurrenceJobKind.RECURRENCE_DELETE
                && kind != GoogleCalendarRecurrenceJobKind.OVERRIDE_DELETE)
            && targetPayload == null
        || (kind.isRecurrenceJob()
            && kind != GoogleCalendarRecurrenceJobKind.RECURRENCE_DELETE
            && !targetPayload.hasRecurrence())
        || (kind == GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT
            && targetPayload.hasRecurrence())) {
      throw new IllegalArgumentException("Google recurrence job fields are required");
    }
    if ((kind == GoogleCalendarRecurrenceJobKind.RECURRENCE_CREATE) && !hasText(providerIdentity)) {
      throw new IllegalArgumentException(
          "Only Google recurrence-event create requires provider identity");
    }
    GoogleCalendarRecurrenceJob job = new GoogleCalendarRecurrenceJob();
    job.initialize(operationId, integrationId, accountId, integrationSequence, runnableAt);
    job.kind = kind;
    job.target = GoogleCalendarRecurrenceJobTarget.forKind(kind, recurrenceEventId, originStartAt);
    job.targetPayload = targetPayload;
    job.providerIdentity = providerIdentity;
    return job;
  }

  public GoogleCalendarRecurrenceJobKind getKind() {
    return kind;
  }

  public Long getRecurrenceEventId() {
    return target.recurrenceEventId();
  }

  public Instant getOriginStartAt() {
    return target.originStartAt();
  }

  public GoogleRecurrenceJobPayload getTargetPayload() {
    return targetPayload;
  }

  public String getProviderIdentity() {
    return providerIdentity;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
