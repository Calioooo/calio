package com.calio.calendar.vote.domain;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

@Entity
@Table(
        name = "votes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vote_participant_unavailable_date",
                columnNames = {"vote_participant_id", "unavailable_date"}
        )
)
public class Vote extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_participant_id", nullable = false)
    private VoteParticipant voteParticipant;

    @Column(name = "unavailable_date", nullable = false)
    private LocalDate unavailableDate;

    protected Vote() {
    }

    public Vote(VoteParticipant voteParticipant, LocalDate unavailableDate) {
        this.voteParticipant = voteParticipant;
        this.unavailableDate = unavailableDate;
    }

    public Long getId() {
        return id;
    }

    public VoteParticipant getVoteParticipant() {
        return voteParticipant;
    }

    public LocalDate getUnavailableDate() {
        return unavailableDate;
    }
}
