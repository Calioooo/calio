CREATE TABLE ai_calendar_conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id CHAR(36) NOT NULL,
    account_id BIGINT NOT NULL,
    last_activity_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_calendar_conversations_conversation_id UNIQUE (conversation_id),
    CONSTRAINT fk_ai_calendar_conversations_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE INDEX ix_ai_calendar_conversations_account_activity
    ON ai_calendar_conversations (account_id, last_activity_at, id);

CREATE INDEX ix_ai_calendar_conversations_cleanup
    ON ai_calendar_conversations (last_activity_at, id);

CREATE TABLE ai_calendar_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    message_role VARCHAR(16) NOT NULL,
    message_text TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_calendar_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES ai_calendar_conversations (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_calendar_messages_role
        CHECK (message_role IN ('USER', 'ASSISTANT'))
);

CREATE INDEX ix_ai_calendar_messages_conversation_order
    ON ai_calendar_messages (conversation_id, id);
