ALTER TABLE group_members
    ADD COLUMN status_changed_at DATETIME(6) NULL;

UPDATE group_members
SET status_changed_at = updated_at
WHERE status_changed_at IS NULL;

ALTER TABLE group_members
    MODIFY COLUMN status_changed_at DATETIME(6) NOT NULL;
