-- Deployment precondition: recurrence_events and recurrence_event_overrides must be manually
-- emptied before this migration. There is intentionally no legacy frequency backfill.

ALTER TABLE recurrence_events ADD COLUMN all_day BOOLEAN NOT NULL;
ALTER TABLE recurrence_events ADD COLUMN start_at DATETIME(6) NOT NULL;
ALTER TABLE recurrence_events ADD COLUMN end_at DATETIME(6) NOT NULL;
ALTER TABLE recurrence_events ADD COLUMN time_zone VARCHAR(255);
ALTER TABLE recurrence_events ADD COLUMN recurrence_lines TEXT NOT NULL;
ALTER TABLE recurrence_events MODIFY COLUMN recurrence_start_time TIME(6) NULL;
ALTER TABLE recurrence_events MODIFY COLUMN recurrence_end_time TIME(6) NULL;
ALTER TABLE recurrence_events DROP COLUMN recurrence_frequency;

ALTER TABLE recurrence_event_overrides ADD COLUMN override_title VARCHAR(255);
ALTER TABLE recurrence_event_overrides ADD COLUMN override_description VARCHAR(255);
ALTER TABLE recurrence_event_overrides ADD COLUMN override_all_day BOOLEAN;
ALTER TABLE recurrence_event_overrides ADD COLUMN override_time_zone VARCHAR(255);
