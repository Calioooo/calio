package com.calio.calendar.vote.domain;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vote_rooms")
public class VoteRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private UUID publicId;

    @Column(nullable = false)
    private String name;

    @Embedded
    private VoteCandidateDateRange candidateDateRange;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_account_id")
    private Account createdByAccount;

    protected VoteRoom() {
    }

    public VoteRoom(UUID publicId, String name, LocalDate candidateStartDate, LocalDate candidateEndDate, Account createdByAccount) {
        this(
                publicId,
                name,
                VoteCandidateDateRange.of(candidateStartDate, candidateEndDate),
                createdByAccount
        );
    }

    public VoteRoom(
            UUID publicId,
            String name,
            VoteCandidateDateRange candidateDateRange,
            Account createdByAccount) {
        this.publicId = publicId;
        this.name = name;
        this.candidateDateRange = candidateDateRange;
        this.createdByAccount = createdByAccount;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public String getName() { return name; }
    public LocalDate getCandidateStartDate() { return candidateDateRange.candidateStartDate(); }
    public LocalDate getCandidateEndDate() { return candidateDateRange.candidateEndDate(); }
    public VoteCandidateDateRange getCandidateDateRange() { return candidateDateRange; }
    public Account getCreatedByAccount() { return createdByAccount; }
}
