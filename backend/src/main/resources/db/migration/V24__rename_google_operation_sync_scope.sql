ALTER TABLE google_operation_jobs
    DROP CONSTRAINT ck_google_operation_jobs_target_fields;

UPDATE google_operation_jobs
SET effective_resource_scope = 'SYNC',
    effective_resource_key = 'sync'
WHERE job_kind = 'SYNC'
  AND effective_resource_scope = 'PRIMARY_CALENDAR';

ALTER TABLE google_operation_jobs
    ADD CONSTRAINT ck_google_operation_jobs_target_fields
        CHECK (
            (job_kind = 'SYNC' AND effective_resource_scope = 'SYNC')
            OR
            (job_kind <> 'SYNC' AND target_payload IS NOT NULL)
        );
