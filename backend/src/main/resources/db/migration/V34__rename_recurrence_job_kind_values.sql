ALTER TABLE google_operation_jobs
    DROP CONSTRAINT ck_google_operation_jobs_scope;

ALTER TABLE google_operation_jobs
    MODIFY COLUMN event_operation_kind VARCHAR(64) NULL;

UPDATE google_operation_jobs
SET recurrence_operation_kind = CASE recurrence_operation_kind
    WHEN 'MASTER_CREATE' THEN 'RECURRENCE_CREATE'
    WHEN 'MASTER_UPDATE' THEN 'RECURRENCE_UPDATE'
    WHEN 'MASTER_DELETE' THEN 'RECURRENCE_DELETE'
    ELSE recurrence_operation_kind
END
WHERE recurrence_operation_kind IN ('MASTER_CREATE', 'MASTER_UPDATE', 'MASTER_DELETE');

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
             AND recurrence_operation_kind IN (
                 'RECURRENCE_CREATE',
                 'RECURRENCE_UPDATE',
                 'RECURRENCE_DELETE',
                 'OVERRIDE_UPSERT',
                 'OVERRIDE_DELETE'
             )
             AND recurrence_event_id IS NOT NULL AND recurrence_target_payload IS NOT NULL
             AND job_trigger IS NULL AND event_operation_kind IS NULL AND event_id IS NULL
             AND target_payload IS NULL
             AND ((recurrence_operation_kind = 'RECURRENCE_CREATE'
                   AND origin_start_at IS NULL AND recurrence_provider_identity IS NOT NULL)
                  OR (recurrence_operation_kind IN ('RECURRENCE_UPDATE', 'RECURRENCE_DELETE')
                      AND origin_start_at IS NULL AND recurrence_provider_identity IS NULL)
                  OR (recurrence_operation_kind IN ('OVERRIDE_UPSERT', 'OVERRIDE_DELETE')
                      AND origin_start_at IS NOT NULL AND recurrence_provider_identity IS NULL)))
        );
