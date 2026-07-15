CREATE TABLE google_calendar_integrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    google_subject VARCHAR(255) NOT NULL,
    google_email VARCHAR(320) NOT NULL,
    encrypted_refresh_token TEXT NOT NULL,
    encrypted_access_token TEXT NOT NULL,
    access_token_expires_at DATETIME(6) NOT NULL,
    connected_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_google_calendar_integration_account_id UNIQUE (account_id),
    CONSTRAINT fk_google_calendar_integrations_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);
