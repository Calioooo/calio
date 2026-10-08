ALTER TABLE recurrence_event_overrides MODIFY COLUMN override_id BIGINT NOT NULL;
ALTER TABLE recurrence_event_overrides DROP PRIMARY KEY;
ALTER TABLE recurrence_event_overrides DROP COLUMN override_id;
ALTER TABLE recurrence_event_overrides ADD PRIMARY KEY (recurrence_id, origin_start_at);
ALTER TABLE recurrence_event_overrides DROP COLUMN created_at;
ALTER TABLE recurrence_event_overrides DROP COLUMN updated_at;
