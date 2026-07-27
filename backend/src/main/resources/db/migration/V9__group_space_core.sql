CREATE TABLE group_spaces (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_account_id BIGINT NOT NULL,
    name VARCHAR(30) NOT NULL,
    emoji VARCHAR(64) CHARACTER SET utf8mb4,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_spaces_owner_account
        FOREIGN KEY (owner_account_id) REFERENCES accounts (id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE group_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_space_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    nickname VARCHAR(9) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    active_nickname VARCHAR(9)
        AS (
            CASE WHEN status = 'ACTIVE' THEN nickname ELSE NULL END
        ),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_members_group_space
        FOREIGN KEY (group_space_id) REFERENCES group_spaces (id),
    CONSTRAINT fk_group_members_account
        FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT uk_group_member_group_account UNIQUE (group_space_id, account_id),
    CONSTRAINT uk_group_member_active_nickname UNIQUE (group_space_id, active_nickname),
    CONSTRAINT ck_group_member_status CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    INDEX ix_group_member_account_status (account_id, status),
    INDEX ix_group_member_group_status (group_space_id, status)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
