package com.calio.calendar.vote.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Column(name = "created_by_account_id")
    private Long createdByAccountId;

    protected VoteRoom() {
    }

    public VoteRoom(UUID publicId, String name, LocalDate candidateStartDate, LocalDate candidateEndDate, Long createdByAccountId) {
        this(
                publicId,
                name,
                VoteCandidateDateRange.of(candidateStartDate, candidateEndDate),
                createdByAccountId
        );
    }

    public VoteRoom(
            UUID publicId,
            String name,
            VoteCandidateDateRange candidateDateRange,
            Long createdByAccountId) {
        this.publicId = publicId;
        this.name = name;
        this.candidateDateRange = candidateDateRange;
        this.createdByAccountId = createdByAccountId;
    }

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public String getName() { return name; }
    public LocalDate getCandidateStartDate() { return candidateDateRange.candidateStartDate(); }
    public LocalDate getCandidateEndDate() { return candidateDateRange.candidateEndDate(); }
    public VoteCandidateDateRange getCandidateDateRange() { return candidateDateRange; }
    public Long getCreatedByAccountId() { return createdByAccountId; }
}
