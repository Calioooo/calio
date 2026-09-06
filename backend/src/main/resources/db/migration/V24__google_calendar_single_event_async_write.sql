ALTER TABLE google_calendar_event_mappings
    DROP FOREIGN KEY fk_google_calendar_mappings_event;

ALTER TABLE google_calendar_event_mappings
    DROP INDEX uk_google_calendar_mapping_event_id;

ALTER TABLE google_calendar_event_mappings
    ADD COLUMN local_changed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE google_calendar_event_mappings
    ADD CONSTRAINT uk_google_calendar_mapping_connection_event
        UNIQUE (connection_id, event_id);
