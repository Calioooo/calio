ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN provider_delete_pending BOOLEAN NOT NULL DEFAULT FALSE;
