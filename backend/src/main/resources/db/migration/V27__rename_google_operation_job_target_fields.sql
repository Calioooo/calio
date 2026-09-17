ALTER TABLE google_operation_jobs
    RENAME COLUMN desired_payload TO target_payload;

ALTER TABLE google_operation_jobs
    DROP CONSTRAINT ck_google_operation_jobs_kind_fields;

ALTER TABLE google_operation_jobs
    ADD CONSTRAINT ck_google_operation_jobs_target_fields
        CHECK (
            (job_kind = 'SYNC' AND effective_resource_scope = 'PRIMARY_CALENDAR')
            OR
            (job_kind <> 'SYNC' AND target_payload IS NOT NULL)
        );
