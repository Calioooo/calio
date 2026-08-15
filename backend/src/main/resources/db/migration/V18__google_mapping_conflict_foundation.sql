ALTER TABLE google_calendar_event_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE google_calendar_event_mappings
    ADD COLUMN synced_content_hash VARCHAR(64) NOT NULL;
ALTER TABLE google_calendar_event_mappings
    DROP COLUMN provider_etag;
ALTER TABLE google_calendar_event_mappings
    DROP COLUMN provider_updated_at;
ALTER TABLE google_calendar_event_mappings
    ADD CONSTRAINT ck_google_calendar_event_mappings_sync_status
        CHECK (sync_status IN ('ACTIVE', 'CONFLICTED'));
ALTER TABLE google_calendar_event_mappings
    ADD CONSTRAINT ck_google_calendar_event_mappings_content_hash
        CHECK (REGEXP_LIKE(synced_content_hash, '^[0-9a-f]{64}$'));

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN synced_content_hash VARCHAR(64) NOT NULL;
ALTER TABLE google_calendar_recurrence_event_mappings
    DROP COLUMN provider_etag;
ALTER TABLE google_calendar_recurrence_event_mappings
    DROP COLUMN provider_updated_at;
ALTER TABLE google_calendar_recurrence_event_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_event_mappings_sync_status
        CHECK (sync_status IN ('ACTIVE', 'CONFLICTED'));
ALTER TABLE google_calendar_recurrence_event_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_event_mappings_content_hash
        CHECK (REGEXP_LIKE(synced_content_hash, '^[0-9a-f]{64}$'));

ALTER TABLE google_calendar_recurrence_override_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE google_calendar_recurrence_override_mappings
    ADD COLUMN synced_content_hash VARCHAR(64) NOT NULL;
ALTER TABLE google_calendar_recurrence_override_mappings
    DROP COLUMN provider_etag;
ALTER TABLE google_calendar_recurrence_override_mappings
    DROP COLUMN provider_updated_at;
ALTER TABLE google_calendar_recurrence_override_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_override_mappings_sync_status
        CHECK (sync_status IN ('ACTIVE', 'CONFLICTED'));
ALTER TABLE google_calendar_recurrence_override_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_override_mappings_content_hash
        CHECK (REGEXP_LIKE(synced_content_hash, '^[0-9a-f]{64}$'));

ALTER TABLE google_operation_jobs
    ADD COLUMN target_content_hash VARCHAR(64) NULL;
ALTER TABLE google_operation_jobs
    ADD COLUMN conflict_detected BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE google_operation_jobs
    ADD CONSTRAINT ck_google_operation_jobs_target_content_hash
        CHECK (
            (job_kind = 'SYNC' AND target_content_hash IS NULL)
            OR
            (job_kind <> 'SYNC'
                AND target_content_hash IS NOT NULL
                AND REGEXP_LIKE(target_content_hash, '^[0-9a-f]{64}$'))
        );

CREATE INDEX idx_google_operation_jobs_pending_scope
    ON google_operation_jobs (
        account_id, integration_id, effective_resource_scope,
        effective_resource_key, job_state, account_sequence
    );
