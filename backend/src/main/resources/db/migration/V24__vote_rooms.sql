CREATE TABLE vote_rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    candidate_start_date DATE NOT NULL,
    candidate_end_date DATE NOT NULL,
    created_by_account_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_vote_rooms_public_id UNIQUE (public_id),
    CONSTRAINT fk_vote_rooms_created_by_account
        FOREIGN KEY (created_by_account_id) REFERENCES accounts (id) ON DELETE SET NULL
);
