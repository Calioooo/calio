package com.calio.calendar.vote.domain;

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

    @Column(name = "candidate_start_date", nullable = false)
    private LocalDate candidateStartDate;

    @Column(name = "candidate_end_date", nullable = false)
    private LocalDate candidateEndDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_account_id")
    private Account createdByAccount;

    protected VoteRoom() {
    }

    public VoteRoom(UUID publicId, String name, LocalDate candidateStartDate, LocalDate candidateEndDate, Account createdByAccount) {
        this.publicId = publicId;
        this.name = name;
        this.candidateStartDate = candidateStartDate;
        this.candidateEndDate = candidateEndDate;
        this.createdByAccount = createdByAccount;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public String getName() { return name; }
    public LocalDate getCandidateStartDate() { return candidateStartDate; }
    public LocalDate getCandidateEndDate() { return candidateEndDate; }
    public Account getCreatedByAccount() { return createdByAccount; }
}
