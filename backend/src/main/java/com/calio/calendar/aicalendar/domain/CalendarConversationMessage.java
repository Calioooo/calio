package com.calio.calendar.aicalendar.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_calendar_messages")
public class CalendarConversationMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private CalendarConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_role", nullable = false, length = 16)
    private CalendarConversationMessageRole role;

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "assistant_response_blocks_json", columnDefinition = "TEXT")
    private String assistantResponseBlocksJson;

    protected CalendarConversationMessage() {
    }

    public CalendarConversationMessage(
            CalendarConversation conversation,
            CalendarConversationMessageRole role,
            String text,
            String assistantResponseBlocksJson
    ) {
        this.conversation = conversation;
        this.role = role;
        this.text = text;
        this.assistantResponseBlocksJson = assistantResponseBlocksJson;
    }

    public Long getId() {
        return id;
    }

    public CalendarConversationMessageRole getRole() {
        return role;
    }

    public String getText() {
        return text;
    }

    public String getAssistantResponseBlocksJson() {
        return assistantResponseBlocksJson;
    }
}
