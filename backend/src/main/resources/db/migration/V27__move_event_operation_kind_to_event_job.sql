ALTER TABLE google_operation_jobs
    DROP CONSTRAINT ck_google_operation_jobs_scope;

ALTER TABLE google_operation_jobs
    RENAME COLUMN job_kind TO event_operation_kind;

UPDATE google_operation_jobs
SET event_operation_kind = NULL
WHERE job_scope = 'SYNC';

ALTER TABLE google_operation_jobs
    ADD CONSTRAINT ck_google_operation_jobs_scope
        CHECK (
            (job_scope = 'SYNC' AND event_operation_kind IS NULL AND event_id IS NULL)
            OR
            (job_scope = 'EVENT' AND event_operation_kind IN ('CREATE', 'UPDATE', 'DELETE')
             AND event_id IS NOT NULL AND target_payload IS NOT NULL)
        );
