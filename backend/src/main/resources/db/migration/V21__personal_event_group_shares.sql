CREATE TABLE personal_event_group_shares (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    group_space_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_personal_event_group_share_event_group UNIQUE (event_id, group_space_id),
    CONSTRAINT fk_personal_event_group_share_event
        FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_personal_event_group_share_group_space
        FOREIGN KEY (group_space_id) REFERENCES group_spaces (id)
);
