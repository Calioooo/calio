ALTER TABLE tags
    ADD COLUMN is_fallback BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tags
SET is_fallback = TRUE
WHERE tag_type = 'PERSONAL_DEFAULT'
  AND title = '기타'
  AND account_id IS NULL
  AND group_space_id IS NULL;

UPDATE tags
SET is_fallback = TRUE
WHERE tag_type = 'GROUP_DEFAULT'
  AND account_id IS NULL
  AND group_space_id IS NOT NULL;

ALTER TABLE tags
    ADD COLUMN personal_fallback_marker TINYINT
        AS (CASE
            WHEN tag_type = 'PERSONAL_DEFAULT'
                AND is_fallback = TRUE
                AND account_id IS NULL
                AND group_space_id IS NULL
            THEN 1
            ELSE NULL
        END);

ALTER TABLE tags
    ADD CONSTRAINT uk_tags_personal_fallback UNIQUE (personal_fallback_marker);

ALTER TABLE tags
    ADD CONSTRAINT ck_tags_fallback_role CHECK (
        (tag_type = 'PERSONAL_DEFAULT')
        OR (tag_type = 'GROUP_DEFAULT' AND is_fallback = TRUE)
        OR (tag_type = 'CUSTOM' AND is_fallback = FALSE)
    );
