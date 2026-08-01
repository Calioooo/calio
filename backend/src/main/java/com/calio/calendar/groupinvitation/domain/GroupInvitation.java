package com.calio.calendar.groupinvitation.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;

@Entity
@Table(name = "group_invitations")
public class GroupInvitation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_space_id", nullable = false)
    private Long groupSpaceId;

    @Column(name = "created_by_member_id", nullable = false)
    private Long createdByMemberId;

    @Column(name = "link_token_hash", nullable = false, length = 32, columnDefinition = "BINARY(32)")
    private byte[] linkTokenHash;

    @Column(name = "invite_code_hash", nullable = false, length = 32, columnDefinition = "BINARY(32)")
    private byte[] inviteCodeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected GroupInvitation() {
    }

    public GroupInvitation(
            Long groupSpaceId,
            Long createdByMemberId,
            byte[] linkTokenHash,
            byte[] inviteCodeHash,
            Instant expiresAt
    ) {
        this.groupSpaceId = groupSpaceId;
        this.createdByMemberId = createdByMemberId;
        this.linkTokenHash = Arrays.copyOf(linkTokenHash, linkTokenHash.length);
        this.inviteCodeHash = Arrays.copyOf(inviteCodeHash, inviteCodeHash.length);
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupSpaceId() {
        return groupSpaceId;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public byte[] getLinkTokenHash() {
        return Arrays.copyOf(linkTokenHash, linkTokenHash.length);
    }

    public byte[] getInviteCodeHash() {
        return Arrays.copyOf(inviteCodeHash, inviteCodeHash.length);
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
