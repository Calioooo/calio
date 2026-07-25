ALTER TABLE events
    ADD COLUMN all_day BOOLEAN NULL AFTER end_at;

UPDATE events
SET all_day = FALSE
WHERE all_day IS NULL;

ALTER TABLE events
    MODIFY COLUMN all_day BOOLEAN NOT NULL;

ALTER TABLE events
    MODIFY COLUMN title TEXT NOT NULL;

ALTER TABLE events
    MODIFY COLUMN description TEXT NULL;

CREATE INDEX idx_events_account_start_at
    ON events (account_id, start_at);

ALTER TABLE google_calendar_integrations
    ADD COLUMN next_sync_token TEXT NULL;

ALTER TABLE google_calendar_integrations
    ADD COLUMN active_sync_run_id VARCHAR(36) NULL;

ALTER TABLE google_calendar_integrations
    ADD COLUMN sync_lease_expires_at DATETIME(6) NULL;

ALTER TABLE google_calendar_integrations
    ADD CONSTRAINT ck_google_calendar_integrations_sync_lease
        CHECK (
            (active_sync_run_id IS NULL AND sync_lease_expires_at IS NULL)
            OR
            (active_sync_run_id IS NOT NULL AND sync_lease_expires_at IS NOT NULL)
        );

CREATE TABLE google_calendar_event_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    integration_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    calendar_key VARCHAR(32) NOT NULL,
    external_event_id VARCHAR(512) NOT NULL,
    provider_etag VARCHAR(255),
    provider_updated_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_google_calendar_mapping_external_identity
        UNIQUE (integration_id, calendar_key, external_event_id),
    CONSTRAINT uk_google_calendar_mapping_event_id UNIQUE (event_id),
    CONSTRAINT fk_google_calendar_mappings_integration
        FOREIGN KEY (integration_id) REFERENCES google_calendar_integrations (id),
    CONSTRAINT fk_google_calendar_mappings_event
        FOREIGN KEY (event_id) REFERENCES events (id)
);
