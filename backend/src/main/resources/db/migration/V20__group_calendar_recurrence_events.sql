CREATE TABLE group_calendar_recurrence_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_space_id BIGINT NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    recurrence_title VARCHAR(255) NOT NULL,
    recurrence_description TEXT NULL,
    all_day BOOLEAN NOT NULL,
    time_zone VARCHAR(255) NULL,
    first_occurrence_start_at DATETIME(6) NOT NULL,
    first_occurrence_end_at DATETIME(6) NOT NULL,
    recurrence_rule TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_recurrence_group FOREIGN KEY (group_space_id) REFERENCES group_spaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_recurrence_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_group_recurrence_tag FOREIGN KEY (tag_id) REFERENCES tags (id)
);

CREATE TABLE group_calendar_recurrence_overrides (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recurrence_event_id BIGINT NOT NULL,
    origin_start_at DATETIME(6) NOT NULL,
    override_title VARCHAR(255) NULL,
    override_description TEXT NULL,
    override_start_at DATETIME(6) NULL,
    override_end_at DATETIME(6) NULL,
    override_all_day BOOLEAN NULL,
    override_time_zone VARCHAR(255) NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_recurrence_override UNIQUE (recurrence_event_id, origin_start_at),
    CONSTRAINT fk_group_recurrence_override FOREIGN KEY (recurrence_event_id) REFERENCES group_calendar_recurrence_events (id) ON DELETE CASCADE
);
