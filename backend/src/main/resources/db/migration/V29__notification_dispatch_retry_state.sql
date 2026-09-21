ALTER TABLE notification_dispatches
    ADD COLUMN dispatch_state VARCHAR(32) NOT NULL DEFAULT 'COMPLETED';

ALTER TABLE notification_dispatches
    ADD COLUMN owner_token VARCHAR(36);

ALTER TABLE notification_dispatches
    ADD COLUMN lease_expires_at DATETIME(6);

ALTER TABLE notification_dispatches
    ADD COLUMN runnable_at DATETIME(6);

ALTER TABLE notification_dispatches
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_notification_dispatches_retry
    ON notification_dispatches (dispatch_state, runnable_at, lease_expires_at);
