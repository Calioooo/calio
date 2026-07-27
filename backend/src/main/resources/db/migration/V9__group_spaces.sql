CREATE TABLE group_spaces (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    emoji VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
    owner_account_id BIGINT NOT NULL,
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
    nickname VARCHAR(9) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    status VARCHAR(16) NOT NULL,
    active_nickname VARCHAR(9)
        GENERATED ALWAYS AS (
            CASE WHEN status = 'ACTIVE' THEN nickname ELSE NULL END
        ),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_members_group_account UNIQUE (group_space_id, account_id),
    CONSTRAINT uk_group_members_active_nickname UNIQUE (group_space_id, active_nickname),
    CONSTRAINT fk_group_members_group_space
        FOREIGN KEY (group_space_id) REFERENCES group_spaces (id),
    CONSTRAINT fk_group_members_account
        FOREIGN KEY (account_id) REFERENCES accounts (id),
    INDEX idx_group_members_account_status_updated (account_id, status, updated_at, group_space_id),
    INDEX idx_group_members_group_status (group_space_id, status, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE group_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_space_id BIGINT NOT NULL,
    issuer_member_id BIGINT NOT NULL,
    link_token_hash BINARY(32) NOT NULL,
    invite_code_hash BINARY(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_invitations_link_token_hash UNIQUE (link_token_hash),
    CONSTRAINT uk_group_invitations_invite_code_hash UNIQUE (invite_code_hash),
    CONSTRAINT fk_group_invitations_group_space
        FOREIGN KEY (group_space_id) REFERENCES group_spaces (id),
    CONSTRAINT fk_group_invitations_issuer_member
        FOREIGN KEY (issuer_member_id) REFERENCES group_members (id),
    INDEX idx_group_invitations_group_issuer (group_space_id, issuer_member_id, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
