package com.calio.calendar.groupspace.domain;

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
import java.time.Instant;
import java.util.Arrays;

@Entity
@Table(name = "group_invitations")
public class GroupInvitation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_space_id", nullable = false)
    private GroupSpace groupSpace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuer_member_id", nullable = false)
    private GroupMember issuer;

    @Column(name = "link_token_hash", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] linkTokenHash;

    @Column(name = "invite_code_hash", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] inviteCodeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    protected GroupInvitation() {
    }

    public GroupInvitation(
            GroupSpace groupSpace,
            GroupMember issuer,
            byte[] linkTokenHash,
            byte[] inviteCodeHash,
            Instant expiresAt
    ) {
        this.groupSpace = groupSpace;
        this.issuer = issuer;
        this.linkTokenHash = Arrays.copyOf(linkTokenHash, linkTokenHash.length);
        this.inviteCodeHash = Arrays.copyOf(inviteCodeHash, inviteCodeHash.length);
        this.expiresAt = expiresAt;
    }

    public boolean matches(InvitationCredentialType type, byte[] digest) {
        byte[] storedDigest = type == InvitationCredentialType.LINK_TOKEN
                ? linkTokenHash
                : inviteCodeHash;
        return Arrays.equals(storedDigest, digest);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public GroupSpace getGroupSpace() {
        return groupSpace;
    }

    public GroupMember getIssuer() {
        return issuer;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
