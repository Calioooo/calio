ALTER TABLE vote_participants
    ADD COLUMN account_id BIGINT NULL;

ALTER TABLE vote_participants
    ADD CONSTRAINT uk_vote_participant_room_account
        UNIQUE (vote_room_id, account_id);

CREATE INDEX idx_vote_participants_account_updated
    ON vote_participants (account_id, updated_at);
