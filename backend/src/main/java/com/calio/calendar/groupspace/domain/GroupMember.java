package com.calio.calendar.groupspace.domain;

import com.calio.calendar.account.domain.Account;
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

@Entity
@Table(name = "group_members")
public class GroupMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_space_id", nullable = false)
    private GroupSpace groupSpace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 9)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GroupMemberStatus status;

    protected GroupMember() {
    }

    public GroupMember(GroupSpace groupSpace, Account account, String nickname) {
        this.groupSpace = groupSpace;
        this.account = account;
        this.nickname = nickname;
        this.status = GroupMemberStatus.ACTIVE;
    }

    public void rejoin(String nickname) {
        this.nickname = nickname;
        this.status = GroupMemberStatus.ACTIVE;
    }

    public void leave() {
        this.status = GroupMemberStatus.LEFT;
    }

    public void remove() {
        this.status = GroupMemberStatus.REMOVED;
    }

    public boolean isActive() {
        return status == GroupMemberStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public GroupSpace getGroupSpace() {
        return groupSpace;
    }

    public Account getAccount() {
        return account;
    }

    public Long getAccountId() {
        return account.getId();
    }

    public String getNickname() {
        return nickname;
    }

    public GroupMemberStatus getStatus() {
        return status;
    }
}
