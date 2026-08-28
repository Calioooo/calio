ALTER TABLE google_calendar_integrations
    ADD COLUMN next_google_operation_sequence BIGINT NOT NULL DEFAULT 1;

ALTER TABLE google_calendar_integrations
    ADD COLUMN google_operation_lease_owner VARCHAR(36) NULL;

ALTER TABLE google_calendar_integrations
    ADD COLUMN google_operation_lease_expires_at DATETIME(6) NULL;

UPDATE google_calendar_integrations integration
SET integration.next_google_operation_sequence = (
    SELECT connection.next_google_operation_sequence
    FROM google_calendar_connections connection
    WHERE connection.integration_id = integration.id
),
    integration.google_operation_lease_owner = (
        SELECT connection.google_operation_lease_owner
        FROM google_calendar_connections connection
        WHERE connection.integration_id = integration.id
    ),
    integration.google_operation_lease_expires_at = (
        SELECT connection.google_operation_lease_expires_at
        FROM google_calendar_connections connection
        WHERE connection.integration_id = integration.id
    );

ALTER TABLE google_operation_jobs
    DROP FOREIGN KEY fk_google_operation_jobs_connection;

ALTER TABLE google_operation_jobs
    DROP CONSTRAINT uk_google_operation_jobs_account_sequence;

DROP INDEX idx_google_operation_jobs_account_head ON google_operation_jobs;

DROP INDEX idx_google_operation_jobs_pending_scope ON google_operation_jobs;

ALTER TABLE google_operation_jobs
    RENAME COLUMN connection_id TO integration_id;

ALTER TABLE google_operation_jobs
    RENAME COLUMN account_sequence TO integration_sequence;

UPDATE google_operation_jobs job
SET job.integration_id = (
    SELECT connection.integration_id
    FROM google_calendar_connections connection
    WHERE connection.id = job.integration_id
);

ALTER TABLE google_operation_jobs
    ADD CONSTRAINT fk_google_operation_jobs_integration
        FOREIGN KEY (integration_id) REFERENCES google_calendar_integrations (id);

ALTER TABLE google_operation_jobs
    ADD CONSTRAINT uk_google_operation_jobs_integration_sequence
        UNIQUE (integration_id, integration_sequence);

CREATE INDEX idx_google_operation_jobs_integration_head
    ON google_operation_jobs (integration_id, integration_sequence, job_state);

CREATE INDEX idx_google_operation_jobs_pending_scope
    ON google_operation_jobs (
        account_id, integration_id, effective_resource_scope,
        effective_resource_key, job_state, integration_sequence
    );

ALTER TABLE google_calendar_connections
    DROP CONSTRAINT ck_google_calendar_integrations_operation_lease;

ALTER TABLE google_calendar_integrations
    ADD CONSTRAINT ck_google_calendar_integrations_operation_lease
        CHECK (
            (google_operation_lease_owner IS NULL AND google_operation_lease_expires_at IS NULL)
            OR
            (google_operation_lease_owner IS NOT NULL AND google_operation_lease_expires_at IS NOT NULL)
        );

ALTER TABLE google_calendar_connections
    DROP CONSTRAINT ck_google_calendar_integrations_lifecycle;

ALTER TABLE google_calendar_connections
    ADD CONSTRAINT ck_google_calendar_connections_lifecycle
        CHECK (
            (connection_state = 'CONNECTED'
                AND encrypted_refresh_token IS NOT NULL
                AND encrypted_access_token IS NOT NULL
                AND access_token_expires_at IS NOT NULL
                AND disconnected_at IS NULL
                AND sync_error_reason IS NULL
                AND sync_error_at IS NULL)
            OR
            (connection_state = 'DISCONNECTED'
                AND encrypted_refresh_token IS NULL
                AND encrypted_access_token IS NULL
                AND access_token_expires_at IS NULL
                AND next_sync_token IS NULL
                AND disconnected_at IS NOT NULL
                AND sync_error_reason IS NULL
                AND sync_error_at IS NULL)
            OR
            (connection_state = 'SYNC_ERROR'
                AND encrypted_refresh_token IS NOT NULL
                AND encrypted_access_token IS NOT NULL
                AND access_token_expires_at IS NOT NULL
                AND disconnected_at IS NULL
                AND sync_error_reason IS NOT NULL
                AND sync_error_at IS NOT NULL)
        );

ALTER TABLE google_calendar_connections
    DROP COLUMN next_google_operation_sequence;

ALTER TABLE google_calendar_connections
    DROP COLUMN google_operation_lease_owner;

ALTER TABLE google_calendar_connections
    DROP COLUMN google_operation_lease_expires_at;
