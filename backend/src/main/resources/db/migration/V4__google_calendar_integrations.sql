CREATE TABLE google_calendar_integrations (
    google_calendar_integration_id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    google_subject VARCHAR(255) NOT NULL,
    google_email VARCHAR(255) NOT NULL,
    encrypted_refresh_token VARCHAR(2048) NOT NULL,
    access_token VARCHAR(2048) NOT NULL,
    access_token_expires_at DATETIME(6) NOT NULL,
    connection_status VARCHAR(255) NOT NULL,
    connected_at DATETIME(6),
    disconnected_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (google_calendar_integration_id),
    CONSTRAINT uk_google_calendar_integration_account_id UNIQUE (account_id),
    CONSTRAINT fk_google_calendar_integrations_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);
