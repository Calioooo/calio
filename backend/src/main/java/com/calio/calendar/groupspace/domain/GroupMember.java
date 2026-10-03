package com.calio.calendar.groupspace.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "group_members")
public class GroupMember extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "group_space_id", nullable = false)
  private Long groupSpaceId;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private GroupMemberStatus status;

  @Embedded
  @AttributeOverride(
      name = "value",
      column = @Column(name = "nickname", nullable = false, length = 9))
  private GroupMemberNickname nickname;

  @Column(name = "is_anonymous", nullable = false)
  private boolean isAnonymous;

  @Column(name = "status_changed_at", nullable = false)
  private Instant statusChangedAt;

  protected GroupMember() {}

  public GroupMember(Long groupSpaceId, Long accountId, GroupMemberNickname nickname, Instant now) {
    this.groupSpaceId = groupSpaceId;
    this.accountId = accountId;
    this.status = GroupMemberStatus.ACTIVE;
    this.nickname = nickname;
    this.statusChangedAt = normalize(now);
  }

  public GroupMember(GroupSpace groupSpace, Long accountId, String nickname, Instant now) {
    this(groupSpace.getId(), accountId, new GroupMemberNickname(nickname), now);
  }

  public Long getId() {
    return id;
  }

  public Long getGroupSpaceId() {
    return groupSpaceId;
  }

  public Long getAccountId() {
    return accountId;
  }

  public GroupMemberStatus getStatus() {
    return status;
  }

  public String getNickname() {
    return nickname.value();
  }

  public Instant getStatusChangedAt() {
    return statusChangedAt;
  }

  public boolean isAnonymous() {
    return isAnonymous;
  }

  public GroupMemberRole roleFor(Long ownerAccountId) {
    return ownerAccountId.equals(accountId) ? GroupMemberRole.OWNER : GroupMemberRole.MEMBER;
  }

  public GroupMemberRole roleIn(GroupSpace groupSpace) {
    return roleFor(groupSpace.getOwnerAccountId());
  }

  public void deactivate(GroupMemberStatus inactiveStatus, Instant now) {
    if (inactiveStatus == null || inactiveStatus == GroupMemberStatus.ACTIVE) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    this.status = inactiveStatus;
    this.statusChangedAt = normalize(now);
  }

  public void reactivate(GroupMemberNickname nickname, Instant now) {
    this.status = GroupMemberStatus.ACTIVE;
    this.nickname = nickname;
    this.statusChangedAt = normalize(now);
  }

  public void changeAnonymous(boolean isAnonymous) {
    this.isAnonymous = isAnonymous;
  }

  private static Instant normalize(Instant instant) {
    return instant.truncatedTo(ChronoUnit.MICROS);
  }
}
