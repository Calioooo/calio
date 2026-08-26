ALTER TABLE personal_event_group_shares
    ADD COLUMN is_anonymous BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE personal_event_group_shares
    ADD COLUMN public_share_id CHAR(36) NULL;

UPDATE personal_event_group_shares
SET is_anonymous = NOT show_original_details,
    public_share_id = UUID()
WHERE public_share_id IS NULL;

ALTER TABLE personal_event_group_shares
    MODIFY COLUMN public_share_id CHAR(36) NOT NULL;

ALTER TABLE personal_event_group_shares
    ADD CONSTRAINT uk_personal_event_group_share_public_id UNIQUE (public_share_id);
