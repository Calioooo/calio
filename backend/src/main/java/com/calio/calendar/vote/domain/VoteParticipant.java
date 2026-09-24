package com.calio.calendar.vote.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "vote_participants",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_vote_participant_room_nickname",
            columnNames = {"vote_room_id", "nickname"}))
public class VoteParticipant extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "vote_room_id", nullable = false)
  private Long voteRoomId;

  @Embedded
  @AttributeOverride(
      name = "value",
      column = @Column(name = "nickname", nullable = false, length = 9))
  private VoteParticipantNickname nickname;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "account_id")
  private Long accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private VoteParticipantStatus status;

  protected VoteParticipant() {}

  public VoteParticipant(Long voteRoomId, String nickname, String passwordHash) {
    this(voteRoomId, VoteParticipantNickname.of(nickname), passwordHash);
  }

  public VoteParticipant(Long voteRoomId, VoteParticipantNickname nickname, String passwordHash) {
    this.voteRoomId = voteRoomId;
    this.nickname = nickname;
    this.passwordHash = passwordHash;
    this.status = VoteParticipantStatus.REGISTERED;
  }

  public static VoteParticipant forAccount(Long voteRoomId, String nickname, Long accountId) {
    return forAccount(voteRoomId, VoteParticipantNickname.of(nickname), null, accountId);
  }

  public static VoteParticipant forAccount(
      Long voteRoomId, String nickname, String passwordHash, Long accountId) {
    return forAccount(voteRoomId, VoteParticipantNickname.of(nickname), passwordHash, accountId);
  }

  public static VoteParticipant forAccount(
      Long voteRoomId, VoteParticipantNickname nickname, Long accountId) {
    return forAccount(voteRoomId, nickname, null, accountId);
  }

  public static VoteParticipant forAccount(
      Long voteRoomId, VoteParticipantNickname nickname, String passwordHash, Long accountId) {
    VoteParticipant participant = new VoteParticipant(voteRoomId, nickname, passwordHash);
    participant.accountId = accountId;
    return participant;
  }

  public Long getId() {
    return id;
  }

  public Long getVoteRoomId() {
    return voteRoomId;
  }

  public String getNickname() {
    return nickname.value();
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Long getAccountId() {
    return accountId;
  }

  public VoteParticipantStatus getStatus() {
    return status;
  }

  public boolean hasSubmittedVotes() {
    return status == VoteParticipantStatus.SUBMITTED;
  }

  public boolean hasPassword() {
    return passwordHash != null;
  }

  public void submit() {
    this.status = VoteParticipantStatus.SUBMITTED;
  }
}
