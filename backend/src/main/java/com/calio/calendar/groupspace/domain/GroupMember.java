package com.calio.calendar.groupspace.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "group_members")
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_space_id", nullable = false)
    private GroupSpace groupSpace;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GroupMemberStatus status;

    @Column(nullable = false, length = 9)
    private String nickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    protected GroupMember() {
    }

    public GroupMember(GroupSpace groupSpace, Long accountId, String nickname) {
        this(groupSpace, accountId, nickname, Instant.now());
    }

    public GroupMember(GroupSpace groupSpace, Long accountId, String nickname, Instant now) {
        this.groupSpace = groupSpace;
        this.accountId = accountId;
        this.status = GroupMemberStatus.ACTIVE;
        this.nickname = nickname;
        this.createdAt = normalize(now);
        this.statusChangedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public GroupSpace getGroupSpace() {
        return groupSpace;
    }

    public Long getAccountId() {
        return accountId;
    }

    public GroupMemberStatus getStatus() {
        return status;
    }

    public String getNickname() {
        return nickname;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public GroupMemberRole roleIn(GroupSpace groupSpace) {
        return groupSpace.getOwnerAccountId().equals(accountId)
                ? GroupMemberRole.OWNER
                : GroupMemberRole.MEMBER;
    }

    public void deactivate(GroupMemberStatus inactiveStatus, Instant now) {
        if (inactiveStatus == null || inactiveStatus == GroupMemberStatus.ACTIVE) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
        this.status = inactiveStatus;
        this.statusChangedAt = normalize(now);
    }

    public void reactivate(String nickname, Instant now) {
        this.status = GroupMemberStatus.ACTIVE;
        this.nickname = nickname;
        this.statusChangedAt = normalize(now);
    }

    private static Instant normalize(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}
