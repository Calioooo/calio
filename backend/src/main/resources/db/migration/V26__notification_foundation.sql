CREATE TABLE ios_push_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    installation_id VARCHAR(128) NOT NULL,
    apns_token VARCHAR(512),
    active BOOLEAN NOT NULL,
    environment VARCHAR(32) NOT NULL,
    deactivated_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ios_push_devices_account_installation UNIQUE (account_id, installation_id),
    CONSTRAINT uk_ios_push_devices_apns_token UNIQUE (apns_token),
    CONSTRAINT fk_ios_push_devices_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    INDEX idx_ios_push_devices_eligible (account_id, active)
);
