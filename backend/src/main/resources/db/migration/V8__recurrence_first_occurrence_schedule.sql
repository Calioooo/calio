-- Deployment precondition: recurrence_events and recurrence_event_overrides are empty.
-- Existing V5-V7 migrations remain immutable; no legacy recurrence data is backfilled.

ALTER TABLE recurrence_events ADD COLUMN first_occurrence_start_at DATETIME(6) NOT NULL;
ALTER TABLE recurrence_events ADD COLUMN first_occurrence_end_at DATETIME(6) NOT NULL;

-- Keep an account_id-leading index before removing the former index because
-- fk_recurrence_events_account requires one on its child column.
CREATE INDEX idx_recurrence_events_account_first_occurrence
    ON recurrence_events (account_id, first_occurrence_start_at);

ALTER TABLE recurrence_events DROP INDEX idx_recurrence_events_account_period;

ALTER TABLE recurrence_events DROP COLUMN recurrence_start_date;
ALTER TABLE recurrence_events DROP COLUMN recurrence_end_date;
ALTER TABLE recurrence_events DROP COLUMN recurrence_start_time;
ALTER TABLE recurrence_events DROP COLUMN recurrence_end_time;
