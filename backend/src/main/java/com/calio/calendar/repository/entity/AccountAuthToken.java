package com.calio.calendar.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "account_auth_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_account_auth_tokens_token_hash", columnNames = "token_hash"),
                @UniqueConstraint(name = "uk_account_auth_tokens_account_id", columnNames = "account_id")
        }
)
public class AccountAuthToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected AccountAuthToken() {
    }

    public AccountAuthToken(Account account, String tokenHash) {
        this.account = account;
        this.tokenHash = tokenHash;
    }

    public void markUsedAt(Instant usedAt) {
        this.lastUsedAt = usedAt;
    }

    public void revoke(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return account.getId();
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
