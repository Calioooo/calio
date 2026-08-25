CREATE TABLE personal_recurrence_group_shares (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recurrence_event_id BIGINT NOT NULL,
    group_space_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_personal_recurrence_group_share_recurrence_group
        UNIQUE (recurrence_event_id, group_space_id),
    CONSTRAINT fk_personal_recurrence_group_share_recurrence
        FOREIGN KEY (recurrence_event_id) REFERENCES recurrence_events (id),
    CONSTRAINT fk_personal_recurrence_group_share_group_space
        FOREIGN KEY (group_space_id) REFERENCES group_spaces (id)
);
