INSERT INTO tags (tag_type, title, color_code, account_id, created_at, updated_at)
SELECT 'DEFAULT', '기타', '#64748B', NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1
    FROM tags
    WHERE tag_type = 'DEFAULT'
      AND title = '기타'
      AND account_id IS NULL
);

INSERT INTO tags (tag_type, title, color_code, account_id, created_at, updated_at)
SELECT 'DEFAULT', '업무', '#3B82F6', NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1
    FROM tags
    WHERE tag_type = 'DEFAULT'
      AND title = '업무'
      AND account_id IS NULL
);

INSERT INTO tags (tag_type, title, color_code, account_id, created_at, updated_at)
SELECT 'DEFAULT', '개인', '#A855F7', NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1
    FROM tags
    WHERE tag_type = 'DEFAULT'
      AND title = '개인'
      AND account_id IS NULL
);

INSERT INTO tags (tag_type, title, color_code, account_id, created_at, updated_at)
SELECT 'DEFAULT', '약속', '#F97316', NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1
    FROM tags
    WHERE tag_type = 'DEFAULT'
      AND title = '약속'
      AND account_id IS NULL
);

INSERT INTO tags (tag_type, title, color_code, account_id, created_at, updated_at)
SELECT 'DEFAULT', '공부', '#10B981', NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1
    FROM tags
    WHERE tag_type = 'DEFAULT'
      AND title = '공부'
      AND account_id IS NULL
);
