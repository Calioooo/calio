ALTER TABLE google_calendar_event_mappings
    MODIFY COLUMN provider_etag VARCHAR(1024) NULL;

CREATE TABLE google_calendar_recurrence_event_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    integration_id BIGINT NOT NULL,
    recurrence_event_id BIGINT NOT NULL,
    calendar_key VARCHAR(32) NOT NULL,
    external_event_id VARCHAR(1024)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_etag VARCHAR(1024),
    provider_updated_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_google_calendar_recurrence_event_external
        UNIQUE (integration_id, calendar_key, external_event_id),
    CONSTRAINT uk_google_calendar_recurrence_event_canonical
        UNIQUE (recurrence_event_id),
    CONSTRAINT fk_google_calendar_recurrence_event_integration
        FOREIGN KEY (integration_id) REFERENCES google_calendar_integrations (id),
    CONSTRAINT fk_google_calendar_recurrence_event_canonical
        FOREIGN KEY (recurrence_event_id) REFERENCES recurrence_events (id)
);

CREATE TABLE google_calendar_recurrence_override_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    google_calendar_recurrence_event_mapping_id BIGINT NOT NULL,
    recurrence_event_override_id BIGINT NOT NULL,
    external_event_id VARCHAR(1024)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_etag VARCHAR(1024),
    provider_updated_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_google_calendar_recurrence_override_external
        UNIQUE (google_calendar_recurrence_event_mapping_id, external_event_id),
    CONSTRAINT uk_google_calendar_recurrence_override_canonical
        UNIQUE (recurrence_event_override_id),
    CONSTRAINT fk_google_calendar_recurrence_override_parent
        FOREIGN KEY (google_calendar_recurrence_event_mapping_id)
            REFERENCES google_calendar_recurrence_event_mappings (id),
    CONSTRAINT fk_google_calendar_recurrence_override_canonical
        FOREIGN KEY (recurrence_event_override_id)
            REFERENCES recurrence_event_overrides (override_id)
);
