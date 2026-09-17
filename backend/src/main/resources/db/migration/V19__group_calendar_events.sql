CREATE TABLE group_calendar_events (
 id BIGINT NOT NULL AUTO_INCREMENT, group_space_id BIGINT NOT NULL, created_by_account_id BIGINT NOT NULL, tag_id BIGINT NOT NULL,
 title VARCHAR(255) NOT NULL, description TEXT NULL, start_at DATETIME(6) NOT NULL, end_at DATETIME(6) NOT NULL, all_day BOOLEAN NOT NULL, time_zone VARCHAR(255) NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, PRIMARY KEY(id),
 CONSTRAINT fk_group_calendar_events_group FOREIGN KEY(group_space_id) REFERENCES group_spaces(id) ON DELETE CASCADE,
 CONSTRAINT fk_group_calendar_events_creator FOREIGN KEY(created_by_account_id) REFERENCES accounts(id),
 CONSTRAINT fk_group_calendar_events_tag FOREIGN KEY(tag_id) REFERENCES tags(id), INDEX ix_group_calendar_events_range(group_space_id,start_at,end_at)
);
