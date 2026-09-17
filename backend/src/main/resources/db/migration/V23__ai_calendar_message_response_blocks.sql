ALTER TABLE ai_calendar_messages
    ADD COLUMN assistant_response_blocks_json TEXT NULL;

ALTER TABLE ai_calendar_messages
    ADD CONSTRAINT ck_ai_calendar_messages_response_blocks_role
    CHECK (
        message_role = 'ASSISTANT'
        OR assistant_response_blocks_json IS NULL
    );
