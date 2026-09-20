ALTER TABLE accounts
    ADD COLUMN calendar_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE accounts
    ADD COLUMN timed_reminder_offset VARCHAR(32) NOT NULL DEFAULT 'MINUTES_10';

ALTER TABLE accounts
    ADD COLUMN important_reminder_offset VARCHAR(32) NOT NULL DEFAULT 'MINUTES_120';

ALTER TABLE accounts
    ADD COLUMN all_day_reminder_time TIME NOT NULL DEFAULT '09:00:00';

ALTER TABLE accounts
    ADD COLUMN daily_briefing_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE accounts
    ADD COLUMN daily_briefing_time TIME NOT NULL DEFAULT '08:00:00';

CREATE INDEX idx_accounts_notification_enabled
    ON accounts (calendar_notifications_enabled, id);

CREATE TABLE notification_dispatches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    schedule_key VARCHAR(255) NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_dispatches_claim UNIQUE (account_id, notification_type, schedule_key, scheduled_at),
    CONSTRAINT fk_notification_dispatches_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE INDEX idx_notification_dispatches_scheduled_at
    ON notification_dispatches (scheduled_at);
