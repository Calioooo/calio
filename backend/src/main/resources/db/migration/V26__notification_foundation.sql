CREATE TABLE ios_notification_endpoints (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    installation_id VARCHAR(128) NOT NULL,
    apns_token VARCHAR(512),
    authorization_status VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL,
    environment VARCHAR(32) NOT NULL,
    deactivated_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ios_notification_endpoints_account_installation UNIQUE (account_id, installation_id),
    CONSTRAINT uk_ios_notification_endpoints_apns_token UNIQUE (apns_token),
    CONSTRAINT fk_ios_notification_endpoints_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    INDEX idx_ios_notification_endpoints_eligible (account_id, active, authorization_status)
);

CREATE TABLE account_notification_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    calendar_notifications_enabled BOOLEAN NOT NULL,
    timed_reminder_offset VARCHAR(32) NOT NULL,
    important_reminder_offset VARCHAR(32) NOT NULL,
    all_day_reminder_time TIME NOT NULL,
    daily_briefing_enabled BOOLEAN NOT NULL,
    daily_briefing_time TIME NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_account_notification_settings_account UNIQUE (account_id),
    CONSTRAINT fk_account_notification_settings_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    INDEX idx_account_notification_settings_enabled (calendar_notifications_enabled, account_id)
);

CREATE TABLE notification_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    schedule_key VARCHAR(255) NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    target_date DATE NOT NULL,
    title VARCHAR(255),
    group_name VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_deliveries_claim UNIQUE (account_id, notification_type, schedule_key, scheduled_at),
    CONSTRAINT fk_notification_deliveries_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE TABLE notification_endpoint_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_delivery_id BIGINT NOT NULL,
    endpoint_id BIGINT NOT NULL,
    result VARCHAR(32) NOT NULL,
    provider_request_id VARCHAR(255),
    failure_reason VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_endpoint_deliveries UNIQUE (notification_delivery_id, endpoint_id),
    CONSTRAINT fk_notification_endpoint_deliveries_delivery FOREIGN KEY (notification_delivery_id) REFERENCES notification_deliveries (id),
    CONSTRAINT fk_notification_endpoint_deliveries_endpoint FOREIGN KEY (endpoint_id) REFERENCES ios_notification_endpoints (id)
);
