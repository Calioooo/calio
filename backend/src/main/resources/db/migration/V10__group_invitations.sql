CREATE TABLE group_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_space_id BIGINT NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    link_token_hash BINARY(32) NOT NULL,
    invite_code_hash BINARY(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_invitations_group_space
        FOREIGN KEY (group_space_id) REFERENCES group_spaces (id),
    CONSTRAINT fk_group_invitations_created_by_member
        FOREIGN KEY (created_by_member_id) REFERENCES group_members (id),
    CONSTRAINT uk_group_invitations_link_token_hash UNIQUE (link_token_hash),
    CONSTRAINT uk_group_invitations_invite_code_hash UNIQUE (invite_code_hash),
    INDEX ix_group_invitations_issuer_expiry
        (group_space_id, created_by_member_id, expires_at),
    INDEX ix_group_invitations_expiry (expires_at, id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
