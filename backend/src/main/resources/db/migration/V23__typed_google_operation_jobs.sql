ALTER TABLE google_operation_jobs
    DROP CONSTRAINT ck_google_operation_jobs_target_fields;

ALTER TABLE google_operation_jobs
    ADD COLUMN job_scope VARCHAR(64) NULL;

ALTER TABLE google_operation_jobs
    ADD COLUMN event_id BIGINT NULL;

UPDATE google_operation_jobs
SET job_scope = 'SYNC'
WHERE job_kind = 'SYNC';

UPDATE google_operation_jobs
SET job_scope = 'EVENT',
    event_id = CAST(effective_resource_key AS BIGINT)
WHERE job_kind IN ('CREATE', 'UPDATE', 'DELETE')
  AND effective_resource_scope = 'GENERAL_EVENT';

ALTER TABLE google_operation_jobs
    MODIFY COLUMN job_scope VARCHAR(64) NOT NULL;

DROP INDEX idx_google_operation_jobs_pending_scope ON google_operation_jobs;

ALTER TABLE google_operation_jobs
    DROP COLUMN effective_resource_scope;

ALTER TABLE google_operation_jobs
    DROP COLUMN effective_resource_key;

ALTER TABLE google_operation_jobs
    RENAME COLUMN job_kind TO event_operation_kind;

UPDATE google_operation_jobs
SET event_operation_kind = NULL
WHERE job_scope = 'SYNC';

UPDATE google_operation_jobs
SET job_trigger = NULL
WHERE job_scope = 'EVENT';

ALTER TABLE google_operation_jobs
    MODIFY COLUMN job_trigger VARCHAR(32) NULL;

ALTER TABLE google_operation_jobs
    ADD CONSTRAINT ck_google_operation_jobs_scope
        CHECK (
            (job_scope = 'SYNC' AND event_operation_kind IS NULL AND event_id IS NULL
             AND job_trigger IN ('MANUAL', 'PERIODIC'))
            OR
            (job_scope = 'EVENT' AND event_operation_kind IN ('CREATE', 'UPDATE', 'DELETE')
             AND event_id IS NOT NULL AND target_payload IS NOT NULL AND job_trigger IS NULL)
        );

CREATE INDEX idx_google_operation_jobs_pending_event
    ON google_operation_jobs (account_id, integration_id, event_id, job_state, integration_sequence);
