ALTER TABLE vote_participants
    ADD COLUMN account_id BIGINT NULL;

CREATE INDEX idx_vote_participants_account_updated
    ON vote_participants (account_id, updated_at);
