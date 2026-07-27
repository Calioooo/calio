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

    @Column(name = "owner_account_id", nullable = false)
    private Long ownerAccountId;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 64)
    private String emoji;

    protected GroupSpace() {
    }

    public GroupSpace(Long ownerAccountId, String name, String emoji) {
        this.ownerAccountId = ownerAccountId;
        this.name = name;
        this.emoji = emoji;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerAccountId() {
        return ownerAccountId;
    }

    public String getName() {
        return name;
    }

    public String getEmoji() {
        return emoji;
    }

    public void update(String name, String emoji) {
        this.name = name;
        this.emoji = emoji;
    }
}
