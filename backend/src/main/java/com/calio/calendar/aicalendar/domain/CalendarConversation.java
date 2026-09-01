package com.calio.calendar.aicalendar.domain;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_calendar_conversations")
public class CalendarConversation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, updatable = false, length = 36)
    private String conversationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected CalendarConversation() {
    }

    public CalendarConversation(Account account, Instant createdAt) {
        this.conversationId = UUID.randomUUID().toString();
        this.account = account;
        this.lastActivityAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public Long getAccountId() {
        return account.getId();
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public void touch(Instant activityAt) {
        this.lastActivityAt = activityAt;
    }
}
