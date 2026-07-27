package com.calio.calendar.groupspace.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_members")
public class GroupMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_space_id", nullable = false)
    private Long groupSpaceId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 9)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GroupMemberStatus status;

    protected GroupMember() {
    }

    public GroupMember(Long groupSpaceId, Long accountId, String nickname) {
        this.groupSpaceId = groupSpaceId;
        this.accountId = accountId;
        this.nickname = nickname;
        this.status = GroupMemberStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == GroupMemberStatus.ACTIVE;
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

    public String getNickname() {
        return nickname;
    }

    public GroupMemberStatus getStatus() {
        return status;
    }
}
