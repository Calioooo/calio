ALTER TABLE google_calendar_integrations
    DROP CONSTRAINT ck_google_calendar_integrations_sync_lease;

ALTER TABLE google_calendar_integrations
    DROP COLUMN active_sync_run_id;

ALTER TABLE google_calendar_integrations
    DROP COLUMN sync_lease_expires_at;
