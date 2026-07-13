CREATE TABLE accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tag_type VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    color_code VARCHAR(7) NOT NULL,
    account_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tags_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE TABLE events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    important_event BOOLEAN NOT NULL DEFAULT FALSE,
    recurrence_id BIGINT,
    deleted_at DATETIME(6),
    account_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_events_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_events_tag FOREIGN KEY (tag_id) REFERENCES tags (id)
);

CREATE TABLE recurrence_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recurrence_title VARCHAR(255) NOT NULL,
    recurrence_description VARCHAR(255),
    recurrence_start_date DATE NOT NULL,
    recurrence_end_date DATE NOT NULL,
    recurrence_start_time TIME(6) NOT NULL,
    recurrence_end_time TIME(6) NOT NULL,
    recurrence_frequency VARCHAR(255) NOT NULL,
    account_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recurrence_events_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_recurrence_events_tag FOREIGN KEY (tag_id) REFERENCES tags (id)
);

CREATE TABLE recurrence_event_overrides (
    override_id BIGINT NOT NULL AUTO_INCREMENT,
    recurrence_id BIGINT NOT NULL,
    origin_start_at DATETIME(6) NOT NULL,
    override_start_at DATETIME(6),
    override_end_at DATETIME(6),
    deleted_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (override_id),
    CONSTRAINT uk_recurrence_event_overrides_recurrence_origin UNIQUE (recurrence_id, origin_start_at),
    CONSTRAINT fk_recurrence_event_overrides_recurrence FOREIGN KEY (recurrence_id) REFERENCES recurrence_events (id)
);

CREATE TABLE tasks (
    task_id BIGINT NOT NULL AUTO_INCREMENT,
    task_title VARCHAR(255) NOT NULL,
    completed BOOLEAN NOT NULL,
    completed_at DATETIME(6),
    account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    CONSTRAINT fk_tasks_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE TABLE national_holidays (
    national_holiday_id BIGINT NOT NULL AUTO_INCREMENT,
    holiday_date DATE NOT NULL,
    holiday_title VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (national_holiday_id),
    CONSTRAINT uk_national_holiday_date_title UNIQUE (holiday_date, holiday_title)
);

CREATE TABLE account_auth_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    revoked_at DATETIME(6),
    last_used_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_account_auth_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_account_auth_tokens_account_id UNIQUE (account_id),
    CONSTRAINT fk_account_auth_tokens_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);
