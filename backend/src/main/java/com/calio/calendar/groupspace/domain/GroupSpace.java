package com.calio.calendar.groupspace.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_spaces")
public class GroupSpace extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 128)
    private String emoji;

    @Column(name = "owner_account_id", nullable = false)
    private Long ownerAccountId;

    protected GroupSpace() {
    }

    public GroupSpace(String name, String emoji, Long ownerAccountId) {
        this.name = name;
        this.emoji = emoji;
        this.ownerAccountId = ownerAccountId;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeEmoji(String emoji) {
        this.emoji = emoji;
    }

    public boolean isOwner(Long accountId) {
        return ownerAccountId.equals(accountId);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmoji() {
        return emoji;
    }

    public Long getOwnerAccountId() {
        return ownerAccountId;
    }
}
