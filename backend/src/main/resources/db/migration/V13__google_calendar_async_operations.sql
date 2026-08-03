ALTER TABLE google_calendar_integrations
    MODIFY COLUMN encrypted_refresh_token TEXT NULL;
ALTER TABLE google_calendar_integrations
    MODIFY COLUMN encrypted_access_token TEXT NULL;
ALTER TABLE google_calendar_integrations
    MODIFY COLUMN access_token_expires_at DATETIME(6) NULL;
ALTER TABLE google_calendar_integrations
    ADD COLUMN integration_state VARCHAR(32) NOT NULL DEFAULT 'CONNECTED';
ALTER TABLE google_calendar_integrations
    ADD COLUMN next_operation_sequence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE google_calendar_integrations
    ADD COLUMN disconnected_at DATETIME(6) NULL;

ALTER TABLE google_calendar_integrations
    ADD CONSTRAINT ck_google_calendar_integration_runtime_state
        CHECK (
            (integration_state = 'CONNECTED'
                AND encrypted_refresh_token IS NOT NULL
                AND encrypted_access_token IS NOT NULL
                AND access_token_expires_at IS NOT NULL
                AND disconnected_at IS NULL)
            OR (integration_state = 'DISCONNECTED'
                AND encrypted_refresh_token IS NULL
                AND encrypted_access_token IS NULL
                AND access_token_expires_at IS NULL
                AND next_sync_token IS NULL
                AND active_sync_run_id IS NULL
                AND sync_lease_expires_at IS NULL
                AND disconnected_at IS NOT NULL)
            OR integration_state = 'SYNC_ERROR'
        );

CREATE INDEX idx_google_calendar_integrations_state
    ON google_calendar_integrations (integration_state, account_id);

CREATE TABLE google_calendar_operation_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    integration_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    sequence_number BIGINT NOT NULL,
    operation_kind VARCHAR(48) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    scope_type VARCHAR(48) NOT NULL,
    scope_key VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NULL,
    desired_payload TEXT NULL,
    provider_identity VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NULL,
    job_status VARCHAR(32) NOT NULL,
    runnable_at DATETIME(6) NOT NULL,
    retry_tier INT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    owner_token VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    periodic_dedup_key VARCHAR(32) NULL,
    terminal_reason VARCHAR(1024) NULL,
    terminal_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_google_calendar_operation_id UNIQUE (operation_id),
    CONSTRAINT uk_google_calendar_operation_sequence UNIQUE (account_id, sequence_number),
    CONSTRAINT uk_google_calendar_periodic_dedup UNIQUE (account_id, periodic_dedup_key),
    CONSTRAINT fk_google_calendar_operation_integration
        FOREIGN KEY (integration_id) REFERENCES google_calendar_integrations (id),
    CONSTRAINT fk_google_calendar_operation_account
        FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT ck_google_calendar_operation_terminal
        CHECK (
            (job_status IN ('PENDING', 'PROCESSING') AND terminal_at IS NULL)
            OR (job_status IN ('SKIPPED', 'CONFLICTED', 'SYNC_ERROR') AND terminal_at IS NOT NULL)
        ),
    CONSTRAINT ck_google_calendar_operation_owner
        CHECK (
            (job_status = 'PROCESSING' AND owner_token IS NOT NULL)
            OR (job_status <> 'PROCESSING' AND owner_token IS NULL)
        )
);

CREATE INDEX idx_google_calendar_operation_account_head
    ON google_calendar_operation_jobs (account_id, job_status, sequence_number);

CREATE INDEX idx_google_calendar_operation_runnable
    ON google_calendar_operation_jobs (job_status, runnable_at, account_id, sequence_number);

CREATE INDEX idx_google_calendar_operation_terminal_cleanup
    ON google_calendar_operation_jobs (terminal_at, id);

ALTER TABLE google_calendar_event_mappings
    DROP FOREIGN KEY fk_google_calendar_mappings_event;
ALTER TABLE google_calendar_event_mappings
    MODIFY COLUMN event_id BIGINT NULL;
ALTER TABLE google_calendar_event_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE google_calendar_event_mappings
    ADD COLUMN synced_content_hash VARCHAR(64) NULL;
ALTER TABLE google_calendar_event_mappings
    ADD COLUMN synced_content_hash_version VARCHAR(32) NULL;
ALTER TABLE google_calendar_event_mappings
    ADD COLUMN local_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE google_calendar_event_mappings
    ADD CONSTRAINT fk_google_calendar_mappings_event
        FOREIGN KEY (event_id) REFERENCES events (id);
ALTER TABLE google_calendar_event_mappings
    ADD CONSTRAINT ck_google_calendar_event_mapping_link
        CHECK (
            event_id IS NOT NULL
            OR sync_status = 'CONFLICTED'
            OR local_deleted = TRUE
        );

ALTER TABLE google_calendar_recurrence_event_mappings
    DROP FOREIGN KEY fk_google_calendar_recurrence_event_canonical;
ALTER TABLE google_calendar_recurrence_event_mappings
    MODIFY COLUMN recurrence_event_id BIGINT NULL;
ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN synced_content_hash VARCHAR(64) NULL;
ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN synced_content_hash_version VARCHAR(32) NULL;
ALTER TABLE google_calendar_recurrence_event_mappings
    ADD COLUMN local_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE google_calendar_recurrence_event_mappings
    ADD CONSTRAINT fk_google_calendar_recurrence_event_canonical
        FOREIGN KEY (recurrence_event_id) REFERENCES recurrence_events (id);
ALTER TABLE google_calendar_recurrence_event_mappings
    ADD CONSTRAINT ck_google_calendar_recurrence_mapping_link
        CHECK (
            recurrence_event_id IS NOT NULL
            OR sync_status = 'CONFLICTED'
            OR local_deleted = TRUE
        );

ALTER TABLE google_calendar_recurrence_override_mappings
    ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE google_calendar_recurrence_override_mappings
    ADD COLUMN synced_content_hash VARCHAR(64) NULL;
ALTER TABLE google_calendar_recurrence_override_mappings
    ADD COLUMN synced_content_hash_version VARCHAR(32) NULL;
