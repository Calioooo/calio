ALTER TABLE accounts
    ADD COLUMN next_google_operation_sequence BIGINT NOT NULL DEFAULT 1;

ALTER TABLE accounts
    ADD COLUMN google_operation_lease_owner VARCHAR(36) NULL;

ALTER TABLE accounts
    ADD COLUMN google_operation_lease_expires_at DATETIME(6) NULL;

ALTER TABLE accounts
    ADD CONSTRAINT ck_accounts_google_operation_lease
        CHECK (
            (google_operation_lease_owner IS NULL AND google_operation_lease_expires_at IS NULL)
            OR
            (google_operation_lease_owner IS NOT NULL AND google_operation_lease_expires_at IS NOT NULL)
        );

CREATE TABLE google_operation_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_id VARCHAR(36) NOT NULL,
    integration_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    account_sequence BIGINT NOT NULL,
    job_kind VARCHAR(64) NOT NULL,
    job_trigger VARCHAR(32) NOT NULL,
    effective_resource_scope VARCHAR(64) NOT NULL,
    effective_resource_key VARCHAR(1024) NOT NULL,
    provider_identity VARCHAR(1024) NULL,
    desired_payload JSON NULL,
    job_state VARCHAR(32) NOT NULL,
    runnable_at DATETIME(6) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error_reason VARCHAR(128) NULL,
    owner_token VARCHAR(36) NULL,
    terminal_reason VARCHAR(128) NULL,
    terminal_at DATETIME(6) NULL,
    active_periodic_sync_account_id BIGINT GENERATED ALWAYS AS (
        CASE
            WHEN job_kind = 'SYNC'
                AND job_trigger = 'PERIODIC'
                AND job_state IN ('PENDING', 'PROCESSING')
            THEN account_id
            ELSE NULL
        END
    ),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_google_operation_jobs_operation_id UNIQUE (operation_id),
    CONSTRAINT uk_google_operation_jobs_account_sequence UNIQUE (account_id, account_sequence),
    CONSTRAINT uk_google_operation_jobs_active_periodic_sync
        UNIQUE (active_periodic_sync_account_id),
    CONSTRAINT fk_google_operation_jobs_integration
        FOREIGN KEY (integration_id) REFERENCES google_calendar_integrations (id),
    CONSTRAINT fk_google_operation_jobs_account
        FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT ck_google_operation_jobs_trigger
        CHECK (job_trigger IN ('MANUAL', 'PERIODIC', 'CANONICAL_MUTATION')),
    CONSTRAINT ck_google_operation_jobs_state
        CHECK (job_state IN ('PENDING', 'PROCESSING', 'SKIPPED', 'CONFLICTED', 'SYNC_ERROR')),
    CONSTRAINT ck_google_operation_jobs_state_fields
        CHECK (
            (job_state = 'PENDING' AND owner_token IS NULL AND terminal_reason IS NULL AND terminal_at IS NULL)
            OR
            (job_state = 'PROCESSING' AND owner_token IS NOT NULL AND terminal_reason IS NULL AND terminal_at IS NULL)
            OR
            (job_state IN ('SKIPPED', 'CONFLICTED', 'SYNC_ERROR')
                AND owner_token IS NULL AND terminal_reason IS NOT NULL AND terminal_at IS NOT NULL)
        ),
    CONSTRAINT ck_google_operation_jobs_kind_fields
        CHECK (
            (job_kind = 'SYNC' AND effective_resource_scope = 'PRIMARY_CALENDAR')
            OR
            (job_kind <> 'SYNC' AND desired_payload IS NOT NULL)
        )
);

CREATE INDEX idx_google_operation_jobs_account_head
    ON google_operation_jobs (account_id, account_sequence, job_state);

CREATE INDEX idx_google_operation_jobs_runnable
    ON google_operation_jobs (job_state, runnable_at, account_id);

CREATE INDEX idx_google_operation_jobs_terminal_cleanup
    ON google_operation_jobs (job_state, terminal_at, id);
