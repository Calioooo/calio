package com.calio.calendar.vote.domain;

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "vote_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vote_participant_room_nickname",
                columnNames = {"vote_room_id", "nickname"}
        )
)
public class VoteParticipant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vote_room_id", nullable = false)
    private VoteRoom voteRoom;

    @Column(nullable = false, length = 9)
    private String nickname;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VoteParticipantStatus status;

    protected VoteParticipant() {
    }

    public VoteParticipant(VoteRoom voteRoom, String nickname, String passwordHash) {
        this.voteRoom = voteRoom;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.status = VoteParticipantStatus.REGISTERED;
    }

    public Long getId() {
        return id;
    }

    public VoteRoom getVoteRoom() {
        return voteRoom;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public VoteParticipantStatus getStatus() {
        return status;
    }

    public void submit() {
        this.status = VoteParticipantStatus.SUBMITTED;
    }
}
