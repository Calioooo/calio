ALTER TABLE google_operation_jobs
    ADD COLUMN job_scope VARCHAR(64) NULL,
    ADD COLUMN event_id BIGINT NULL;

UPDATE google_operation_jobs
SET job_scope = 'SYNC'
WHERE job_kind = 'SYNC';

UPDATE google_operation_jobs
SET job_scope = 'EVENT',
    event_id = CAST(effective_resource_key AS UNSIGNED)
WHERE job_kind IN ('CREATE', 'UPDATE', 'DELETE')
  AND effective_resource_scope = 'GENERAL_EVENT';

ALTER TABLE google_operation_jobs
    MODIFY COLUMN job_scope VARCHAR(64) NOT NULL;

ALTER TABLE google_operation_jobs
    ADD CONSTRAINT ck_google_operation_jobs_scope
        CHECK (
            (job_scope = 'SYNC' AND job_kind = 'SYNC')
            OR
            (job_scope = 'EVENT' AND job_kind IN ('CREATE', 'UPDATE', 'DELETE') AND event_id IS NOT NULL)
        );
