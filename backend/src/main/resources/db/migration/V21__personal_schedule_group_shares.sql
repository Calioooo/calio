CREATE TABLE personal_event_group_shares (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    group_space_id BIGINT NOT NULL,
    is_anonymous BOOLEAN NOT NULL,
    public_share_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_personal_event_group_share UNIQUE (event_id, group_space_id),
    CONSTRAINT uk_personal_event_group_share_public_id UNIQUE (public_share_id),
    CONSTRAINT fk_personal_event_group_share_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_personal_event_group_share_group FOREIGN KEY (group_space_id) REFERENCES group_spaces (id),
    INDEX ix_personal_event_group_share_group (group_space_id),
    INDEX ix_personal_event_group_share_event (event_id)
);

CREATE TABLE personal_recurrence_group_shares (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recurrence_event_id BIGINT NOT NULL,
    group_space_id BIGINT NOT NULL,
    is_anonymous BOOLEAN NOT NULL,
    public_share_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_personal_recurrence_group_share UNIQUE (recurrence_event_id, group_space_id),
    CONSTRAINT uk_personal_recurrence_group_share_public_id UNIQUE (public_share_id),
    CONSTRAINT fk_personal_recurrence_group_share_event FOREIGN KEY (recurrence_event_id) REFERENCES recurrence_events (id),
    CONSTRAINT fk_personal_recurrence_group_share_group FOREIGN KEY (group_space_id) REFERENCES group_spaces (id),
    INDEX ix_personal_recurrence_group_share_group (group_space_id),
    INDEX ix_personal_recurrence_group_share_event (recurrence_event_id)
);
