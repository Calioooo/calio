ALTER TABLE google_calendar_event_mappings
    MODIFY COLUMN event_id BIGINT NULL;

ALTER TABLE google_calendar_event_mappings
    ADD COLUMN local_deleted_at DATETIME(6) NULL;

ALTER TABLE google_calendar_event_mappings
    ADD COLUMN local_modified_at DATETIME(6) NULL;

ALTER TABLE google_calendar_event_mappings
    ADD CONSTRAINT ck_google_calendar_event_mapping_canonical_link
        CHECK (
            event_id IS NOT NULL
            OR sync_status = 'CONFLICTED'
            OR local_deleted_at IS NOT NULL
        );

ALTER TABLE google_calendar_recurrence_event_mappings
    MODIFY COLUMN recurrence_event_id BIGINT NULL;

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN local_deleted_at DATETIME(6) NULL;

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN local_modified_at DATETIME(6) NULL;

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_mapping_canonical_link
        CHECK (
            recurrence_event_id IS NOT NULL
            OR sync_status = 'CONFLICTED'
            OR local_deleted_at IS NOT NULL
        );

ALTER TABLE google_calendar_recurrence_override_mappings
    ADD COLUMN local_modified_at DATETIME(6) NULL;
