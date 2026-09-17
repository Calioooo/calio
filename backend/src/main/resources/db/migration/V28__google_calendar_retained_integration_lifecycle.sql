ALTER TABLE google_calendar_integrations
    ADD COLUMN integration_state VARCHAR(32) NOT NULL DEFAULT 'CONNECTED';

ALTER TABLE google_calendar_integrations
    ADD COLUMN disconnected_at DATETIME(6) NULL;

ALTER TABLE google_calendar_integrations
    ADD COLUMN sync_error_reason VARCHAR(128) NULL;

ALTER TABLE google_calendar_integrations
    ADD COLUMN sync_error_at DATETIME(6) NULL;

ALTER TABLE google_calendar_integrations
    MODIFY COLUMN encrypted_refresh_token TEXT NULL;

ALTER TABLE google_calendar_integrations
    MODIFY COLUMN encrypted_access_token TEXT NULL;

ALTER TABLE google_calendar_integrations
    MODIFY COLUMN access_token_expires_at DATETIME(6) NULL;

ALTER TABLE google_calendar_integrations
    ADD CONSTRAINT ck_google_calendar_integrations_lifecycle
        CHECK (
            (integration_state = 'CONNECTED'
                AND encrypted_refresh_token IS NOT NULL
                AND encrypted_access_token IS NOT NULL
                AND access_token_expires_at IS NOT NULL
                AND disconnected_at IS NULL
                AND sync_error_reason IS NULL
                AND sync_error_at IS NULL)
            OR
            (integration_state = 'DISCONNECTED'
                AND encrypted_refresh_token IS NULL
                AND encrypted_access_token IS NULL
                AND access_token_expires_at IS NULL
                AND next_sync_token IS NULL
                AND google_operation_lease_owner IS NULL
                AND google_operation_lease_expires_at IS NULL
                AND disconnected_at IS NOT NULL
                AND sync_error_reason IS NULL
                AND sync_error_at IS NULL)
            OR
            (integration_state = 'SYNC_ERROR'
                AND encrypted_refresh_token IS NOT NULL
                AND encrypted_access_token IS NOT NULL
                AND access_token_expires_at IS NOT NULL
                AND disconnected_at IS NULL
                AND sync_error_reason IS NOT NULL
                AND sync_error_at IS NOT NULL)
        );
