package com.calio.calendar.groupinvitation.repository;

import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    Optional<GroupInvitation> findByLinkTokenHash(byte[] linkTokenHash);

    Optional<GroupInvitation> findByInviteCodeHash(byte[] inviteCodeHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from GroupInvitation invitation
            where invitation.id = :invitationId
              and ((:credentialType = 'LINK_TOKEN' and invitation.linkTokenHash = :credentialHash)
                   or (:credentialType = 'INVITE_CODE' and invitation.inviteCodeHash = :credentialHash))
            """)
    Optional<GroupInvitation> findByIdAndCredentialHashForUpdate(
            @Param("invitationId") Long invitationId,
            @Param("credentialType") String credentialType,
            @Param("credentialHash") byte[] credentialHash
    );

    @Query("""
            select invitation.id as invitationId, invitation.expiresAt as expiresAt
            from GroupInvitation invitation
            where invitation.groupSpaceId = :groupSpaceId
              and invitation.createdByMemberId = :createdByMemberId
              and invitation.expiresAt > :now
            order by invitation.expiresAt desc, invitation.id desc
            """)
    List<InvitationSummaryProjection> findActiveSummaries(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("createdByMemberId") Long createdByMemberId,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from GroupInvitation invitation
            where invitation.id = :invitationId
              and invitation.groupSpaceId = :groupSpaceId
              and invitation.createdByMemberId = :createdByMemberId
              and invitation.expiresAt > :now
            """)
    Optional<GroupInvitation> findScopedForUpdate(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("invitationId") Long invitationId,
            @Param("createdByMemberId") Long createdByMemberId,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from GroupInvitation invitation
            where invitation.groupSpaceId = :groupSpaceId
            order by invitation.id
            """)
    List<GroupInvitation> findAllByGroupSpaceIdForUpdateOrderById(
            @Param("groupSpaceId") Long groupSpaceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from GroupInvitation invitation
            where invitation.createdByMemberId = :memberId
            order by invitation.id
            """)
    List<GroupInvitation> findAllByCreatedByMemberIdForUpdateOrderById(
            @Param("memberId") Long memberId
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from GroupInvitation invitation where invitation.createdByMemberId = :memberId")
    int deleteAllByCreatedByMemberId(@Param("memberId") Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GroupInvitation invitation where invitation.groupSpaceId = :groupSpaceId")
    int deleteAllByGroupSpaceId(@Param("groupSpaceId") Long groupSpaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from GroupInvitation invitation
            where invitation.expiresAt <= :cutoff
            order by invitation.expiresAt, invitation.id
            """)
    List<GroupInvitation> findCleanupBatch(
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );
}
