DELETE FROM google_operation_jobs
WHERE job_scope = 'EVENT'
  AND event_id IN (
      SELECT id
      FROM events
      WHERE recurrence_id IS NOT NULL
  );

DELETE FROM google_calendar_event_mappings
WHERE event_id IN (
    SELECT id
    FROM events
    WHERE recurrence_id IS NOT NULL
);

DELETE FROM personal_event_group_shares
WHERE event_id IN (
    SELECT id
    FROM events
    WHERE recurrence_id IS NOT NULL
);

DELETE FROM events
WHERE recurrence_id IS NOT NULL;

ALTER TABLE events
    DROP COLUMN recurrence_id;
