ALTER TABLE tags
    ADD COLUMN group_space_id BIGINT NULL,
    ADD CONSTRAINT fk_tags_group_space FOREIGN KEY (group_space_id) REFERENCES group_spaces (id) ON DELETE CASCADE;

UPDATE tags
SET tag_type = 'PERSONAL_DEFAULT'
WHERE tag_type = 'DEFAULT';

ALTER TABLE tags
    ADD COLUMN group_default_space_id BIGINT
        AS (CASE WHEN tag_type = 'GROUP_DEFAULT' THEN group_space_id ELSE NULL END),
    ADD COLUMN group_custom_title VARCHAR(255)
        AS (CASE WHEN tag_type = 'CUSTOM' AND group_space_id IS NOT NULL THEN title ELSE NULL END),
    ADD CONSTRAINT uk_tags_group_default UNIQUE (group_default_space_id),
    ADD CONSTRAINT uk_tags_group_custom_title UNIQUE (group_space_id, group_custom_title),
    ADD INDEX ix_tags_group_space (group_space_id);
