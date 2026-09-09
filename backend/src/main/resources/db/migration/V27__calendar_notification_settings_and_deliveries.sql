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
