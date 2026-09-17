ALTER TABLE google_calendar_recurrence_event_mappings
    DROP FOREIGN KEY fk_google_calendar_recurrence_event_canonical;

ALTER TABLE google_calendar_recurrence_event_mappings
    DROP INDEX uk_google_calendar_recurrence_event_canonical;

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN local_changed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD CONSTRAINT uk_google_calendar_recurrence_event_connection_canonical
        UNIQUE (connection_id, recurrence_event_id);

CREATE INDEX idx_google_calendar_recurrence_event_mapping_recurrence_id
    ON google_calendar_recurrence_event_mappings (recurrence_event_id);

ALTER TABLE google_calendar_recurrence_override_mappings
    ADD COLUMN origin_start_at DATETIME(6) NULL;

ALTER TABLE google_calendar_recurrence_override_mappings
    ADD COLUMN local_changed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE google_calendar_recurrence_override_mappings mapping
SET origin_start_at = (
    SELECT override_row.origin_start_at
    FROM recurrence_event_overrides override_row
    WHERE override_row.override_id = mapping.recurrence_event_override_id
);

ALTER TABLE google_calendar_recurrence_override_mappings
    DROP FOREIGN KEY fk_google_calendar_recurrence_override_canonical;

ALTER TABLE google_calendar_recurrence_override_mappings
    DROP INDEX uk_google_calendar_recurrence_override_canonical;

ALTER TABLE google_calendar_recurrence_override_mappings
    DROP COLUMN recurrence_event_override_id;

ALTER TABLE google_calendar_recurrence_override_mappings
    MODIFY COLUMN origin_start_at DATETIME(6) NOT NULL;

ALTER TABLE google_calendar_recurrence_override_mappings
    ADD CONSTRAINT uk_google_calendar_recurrence_override_canonical
        UNIQUE (google_calendar_recurrence_event_mapping_id, origin_start_at);

CREATE INDEX idx_google_calendar_recurrence_override_mapping_origin
    ON google_calendar_recurrence_override_mappings
        (google_calendar_recurrence_event_mapping_id, origin_start_at);

ALTER TABLE google_operation_jobs
    DROP CONSTRAINT ck_google_operation_jobs_scope;

ALTER TABLE google_operation_jobs ADD COLUMN recurrence_operation_kind VARCHAR(64) NULL;
ALTER TABLE google_operation_jobs ADD COLUMN recurrence_event_id BIGINT NULL;
ALTER TABLE google_operation_jobs ADD COLUMN origin_start_at DATETIME(6) NULL;
ALTER TABLE google_operation_jobs ADD COLUMN recurrence_target_payload JSON NULL;
ALTER TABLE google_operation_jobs ADD COLUMN recurrence_provider_identity VARCHAR(1024) NULL;

ALTER TABLE google_operation_jobs
    ADD CONSTRAINT ck_google_operation_jobs_scope
        CHECK (
            (job_scope = 'SYNC' AND event_operation_kind IS NULL AND event_id IS NULL
             AND recurrence_operation_kind IS NULL AND recurrence_event_id IS NULL
             AND origin_start_at IS NULL AND recurrence_target_payload IS NULL
             AND recurrence_provider_identity IS NULL
             AND target_payload IS NULL
             AND job_trigger IN ('MANUAL', 'PERIODIC'))
            OR
            (job_scope = 'EVENT' AND event_operation_kind IN ('CREATE', 'UPDATE', 'DELETE')
             AND event_id IS NOT NULL AND target_payload IS NOT NULL AND job_trigger IS NULL
             AND recurrence_operation_kind IS NULL AND recurrence_event_id IS NULL
             AND origin_start_at IS NULL AND recurrence_target_payload IS NULL
             AND recurrence_provider_identity IS NULL)
            OR
            (job_scope = 'RECURRENCE'
             AND recurrence_operation_kind IN ('MASTER_CREATE', 'MASTER_UPDATE', 'MASTER_DELETE', 'OVERRIDE_UPSERT', 'OVERRIDE_DELETE')
             AND recurrence_event_id IS NOT NULL AND recurrence_target_payload IS NOT NULL
             AND job_trigger IS NULL AND event_operation_kind IS NULL AND event_id IS NULL
             AND target_payload IS NULL
             AND ((recurrence_operation_kind = 'MASTER_CREATE'
                   AND origin_start_at IS NULL AND recurrence_provider_identity IS NOT NULL)
                  OR (recurrence_operation_kind IN ('MASTER_UPDATE', 'MASTER_DELETE')
                      AND origin_start_at IS NULL AND recurrence_provider_identity IS NULL)
                  OR (recurrence_operation_kind IN ('OVERRIDE_UPSERT', 'OVERRIDE_DELETE')
                      AND origin_start_at IS NOT NULL AND recurrence_provider_identity IS NULL)))
        );
