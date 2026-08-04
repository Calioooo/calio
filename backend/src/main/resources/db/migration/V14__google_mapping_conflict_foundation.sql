ALTER TABLE google_calendar_event_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE google_calendar_event_mappings
    ADD COLUMN synced_content_hash VARCHAR(67) NOT NULL;

ALTER TABLE google_calendar_event_mappings
    ADD CONSTRAINT ck_google_calendar_event_mappings_sync_status
        CHECK (sync_status IN ('ACTIVE', 'CONFLICTED'));

ALTER TABLE google_calendar_event_mappings
    ADD CONSTRAINT ck_google_calendar_event_mappings_content_hash
        CHECK (synced_content_hash REGEXP '^v1:[0-9a-f]{64}$');

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN synced_content_hash VARCHAR(67) NOT NULL;

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_event_mappings_sync_status
        CHECK (sync_status IN ('ACTIVE', 'CONFLICTED'));

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_event_mappings_content_hash
        CHECK (synced_content_hash REGEXP '^v1:[0-9a-f]{64}$');

ALTER TABLE google_calendar_recurrence_override_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE google_calendar_recurrence_override_mappings
    ADD COLUMN synced_content_hash VARCHAR(67) NOT NULL;

ALTER TABLE google_calendar_recurrence_override_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_override_mappings_sync_status
        CHECK (sync_status IN ('ACTIVE', 'CONFLICTED'));

ALTER TABLE google_calendar_recurrence_override_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_override_mappings_content_hash
        CHECK (synced_content_hash REGEXP '^v1:[0-9a-f]{64}$');

ALTER TABLE google_operation_jobs
    ADD COLUMN desired_content_hash VARCHAR(67) NULL;

ALTER TABLE google_operation_jobs
    ADD COLUMN conflict_detected BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE google_operation_jobs
    MODIFY COLUMN effective_resource_key VARCHAR(2300) NOT NULL;

UPDATE google_operation_jobs
SET desired_content_hash =
        'v1:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
WHERE job_kind <> 'SYNC'
  AND desired_content_hash IS NULL;

ALTER TABLE google_operation_jobs
    ADD CONSTRAINT ck_google_operation_jobs_desired_content_hash
        CHECK (
            (job_kind = 'SYNC' AND desired_content_hash IS NULL)
            OR
            (job_kind <> 'SYNC' AND desired_content_hash REGEXP '^v1:[0-9a-f]{64}$')
        );

CREATE INDEX idx_google_operation_jobs_pending_scope
    ON google_operation_jobs (
        account_id, integration_id, effective_resource_scope,
        job_state, account_sequence
    );
