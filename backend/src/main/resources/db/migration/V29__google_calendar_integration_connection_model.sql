ALTER TABLE google_calendar_integrations RENAME TO google_calendar_connections;

ALTER TABLE google_calendar_connections
    RENAME COLUMN integration_state TO connection_state;

ALTER TABLE google_calendar_connections
    DROP INDEX uk_google_calendar_integration_account_id;

CREATE TABLE google_calendar_integrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_google_calendar_integration_account_id UNIQUE (account_id),
    CONSTRAINT fk_google_calendar_integration_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
);

ALTER TABLE google_calendar_connections
    ADD COLUMN integration_id BIGINT NULL FIRST;

INSERT INTO google_calendar_integrations (account_id, created_at, updated_at)
SELECT account_id, created_at, updated_at
FROM google_calendar_connections;

UPDATE google_calendar_connections connection
SET connection.integration_id = (
    SELECT integration.id
    FROM google_calendar_integrations integration
    WHERE integration.account_id = connection.account_id
);

ALTER TABLE google_calendar_connections
    MODIFY COLUMN integration_id BIGINT NOT NULL;

ALTER TABLE google_calendar_connections
    ADD COLUMN active_connection_marker TINYINT
        GENERATED ALWAYS AS (CASE WHEN connection_state = 'CONNECTED' THEN 1 ELSE NULL END);

ALTER TABLE google_calendar_connections
    ADD CONSTRAINT uk_google_calendar_connections_integration_subject
        UNIQUE (integration_id, google_subject);

ALTER TABLE google_calendar_connections
    ADD CONSTRAINT uk_google_calendar_connections_active
        UNIQUE (integration_id, active_connection_marker);

ALTER TABLE google_calendar_connections
    ADD CONSTRAINT fk_google_calendar_connections_integration
        FOREIGN KEY (integration_id) REFERENCES google_calendar_integrations (id);

ALTER TABLE google_operation_jobs
    DROP FOREIGN KEY fk_google_operation_jobs_integration;

ALTER TABLE google_operation_jobs
    RENAME COLUMN integration_id TO connection_id;

ALTER TABLE google_operation_jobs
    ADD CONSTRAINT fk_google_operation_jobs_connection
        FOREIGN KEY (connection_id) REFERENCES google_calendar_connections (id);

ALTER TABLE google_calendar_event_mappings
    DROP FOREIGN KEY fk_google_calendar_mappings_integration;

ALTER TABLE google_calendar_event_mappings
    RENAME COLUMN integration_id TO connection_id;

ALTER TABLE google_calendar_event_mappings
    DROP INDEX uk_google_calendar_mapping_external_identity;

ALTER TABLE google_calendar_event_mappings
    ADD CONSTRAINT uk_google_calendar_mapping_external_identity
        UNIQUE (connection_id, calendar_key, external_event_id);

ALTER TABLE google_calendar_event_mappings
    ADD CONSTRAINT fk_google_calendar_mappings_connection
        FOREIGN KEY (connection_id) REFERENCES google_calendar_connections (id);

ALTER TABLE google_calendar_recurrence_event_mappings
    DROP FOREIGN KEY fk_google_calendar_recurrence_event_integration;

ALTER TABLE google_calendar_recurrence_event_mappings
    RENAME COLUMN integration_id TO connection_id;

ALTER TABLE google_calendar_recurrence_event_mappings
    DROP INDEX uk_google_calendar_recurrence_event_external;

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD CONSTRAINT uk_google_calendar_recurrence_event_external
        UNIQUE (connection_id, calendar_key, external_event_id);

ALTER TABLE google_calendar_recurrence_event_mappings
    ADD CONSTRAINT fk_google_calendar_recurrence_event_connection
        FOREIGN KEY (connection_id) REFERENCES google_calendar_connections (id);
