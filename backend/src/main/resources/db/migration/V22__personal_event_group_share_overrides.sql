ALTER TABLE personal_event_group_shares
    ADD COLUMN show_original_details BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE personal_event_group_shares
    ADD COLUMN override_title VARCHAR(255) NULL;

ALTER TABLE personal_event_group_shares
    ADD COLUMN override_start_at DATETIME(6) NULL;

ALTER TABLE personal_event_group_shares
    ADD COLUMN override_end_at DATETIME(6) NULL;

ALTER TABLE personal_event_group_shares
    ADD COLUMN override_all_day BOOLEAN NULL;
