ALTER TABLE google_calendar_event_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE google_calendar_event_mappings
    MODIFY COLUMN provider_etag VARCHAR(1024) NOT NULL;
ALTER TABLE google_calendar_event_mappings
    DROP COLUMN provider_updated_at;
ALTER TABLE google_calendar_event_mappings
    ADD CONSTRAINT ck_google_calendar_event_mappings_sync_status
        CHECK (sync_status IN ('ACTIVE', 'CONFLICTED'));
ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE google_calendar_recurrence_event_mappings
    MODIFY COLUMN provider_etag VARCHAR(1024) NOT NULL;
ALTER TABLE google_calendar_recurrence_event_mappings
    DROP COLUMN provider_updated_at;
ALTER TABLE google_calendar_recurrence_event_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_event_mappings_sync_status
        CHECK (sync_status IN ('ACTIVE', 'CONFLICTED'));
ALTER TABLE google_calendar_recurrence_override_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE google_calendar_recurrence_override_mappings
    MODIFY COLUMN provider_etag VARCHAR(1024) NOT NULL;
ALTER TABLE google_calendar_recurrence_override_mappings
    DROP COLUMN provider_updated_at;
ALTER TABLE google_calendar_recurrence_override_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_override_mappings_sync_status
        CHECK (sync_status IN ('ACTIVE', 'CONFLICTED'));
ALTER TABLE google_operation_jobs
    ADD COLUMN conflict_detected BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_google_operation_jobs_pending_scope
    ON google_operation_jobs (
        account_id, integration_id, effective_resource_scope,
        effective_resource_key, job_state, account_sequence
    );
