ALTER TABLE google_operation_jobs
    DROP CONSTRAINT ck_google_operation_jobs_target_fields;

DROP INDEX idx_google_operation_jobs_pending_scope ON google_operation_jobs;

ALTER TABLE google_operation_jobs
    DROP COLUMN effective_resource_scope,
    DROP COLUMN effective_resource_key;

CREATE INDEX idx_google_operation_jobs_pending_event
    ON google_operation_jobs (account_id, integration_id, event_id, job_state, integration_sequence);
