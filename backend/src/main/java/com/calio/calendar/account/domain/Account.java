package com.calio.calendar.account.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(name = "next_google_operation_sequence", nullable = false)
    private long nextGoogleOperationSequence = 1L;

    @jakarta.persistence.Column(name = "google_operation_lease_owner", length = 36)
    private String googleOperationLeaseOwner;

    @jakarta.persistence.Column(name = "google_operation_lease_expires_at")
    private Instant googleOperationLeaseExpiresAt;

    public Account() {
    }

    public Long getId() {
        return id;
    }

    public long allocateGoogleOperationSequence() {
        return nextGoogleOperationSequence++;
    }
}
