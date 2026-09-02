CREATE TABLE vote_participants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    vote_room_id BIGINT NOT NULL,
    nickname VARCHAR(9) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    password_hash VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_vote_participants_vote_room
        FOREIGN KEY (vote_room_id) REFERENCES vote_rooms (id) ON DELETE CASCADE,
    CONSTRAINT uk_vote_participant_room_nickname UNIQUE (vote_room_id, nickname),
    CONSTRAINT ck_vote_participant_status CHECK (status IN ('REGISTERED', 'SUBMITTED'))
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE votes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    vote_participant_id BIGINT NOT NULL,
    unavailable_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_votes_vote_participant
        FOREIGN KEY (vote_participant_id) REFERENCES vote_participants (id) ON DELETE CASCADE,
    CONSTRAINT uk_vote_participant_unavailable_date UNIQUE (vote_participant_id, unavailable_date)
);
