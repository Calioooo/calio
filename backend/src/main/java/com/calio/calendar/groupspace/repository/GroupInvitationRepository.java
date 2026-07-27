package com.calio.calendar.groupspace.repository;

import com.calio.calendar.groupspace.domain.GroupInvitation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {

    Optional<GroupInvitation> findByLinkTokenHash(byte[] linkTokenHash);

    Optional<GroupInvitation> findByInviteCodeHash(byte[] inviteCodeHash);

    @Query("""
            select new com.calio.calendar.groupspace.repository.InvitationLocator(
                invitation.id,
                invitation.groupSpace.id
            )
            from GroupInvitation invitation
            where invitation.linkTokenHash = :digest
            """)
    Optional<InvitationLocator> locateByLinkTokenHash(@Param("digest") byte[] digest);

    @Query("""
            select new com.calio.calendar.groupspace.repository.InvitationLocator(
                invitation.id,
                invitation.groupSpace.id
            )
            from GroupInvitation invitation
            where invitation.inviteCodeHash = :digest
            """)
    Optional<InvitationLocator> locateByInviteCodeHash(@Param("digest") byte[] digest);

    Optional<GroupInvitation> findByIdAndGroupSpace_Id(Long id, Long groupSpaceId);

    List<GroupInvitation> findByGroupSpace_IdOrderByIdDesc(Long groupSpaceId);

    List<GroupInvitation> findByGroupSpace_IdAndIssuer_IdOrderByIdDesc(Long groupSpaceId, Long issuerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from GroupInvitation invitation
            join fetch invitation.issuer
            where invitation.id = :invitationId and invitation.groupSpace.id = :groupSpaceId
            """)
    Optional<GroupInvitation> findByIdForUpdate(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("invitationId") Long invitationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from GroupInvitation invitation
            where invitation.groupSpace.id = :groupSpaceId
            order by invitation.id asc
            """)
    List<GroupInvitation> findAllForUpdate(@Param("groupSpaceId") Long groupSpaceId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GroupInvitation invitation where invitation.issuer.id = :issuerId")
    int deleteByIssuer_Id(@Param("issuerId") Long issuerId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GroupInvitation invitation where invitation.groupSpace.id = :groupSpaceId")
    int deleteByGroupSpace_Id(@Param("groupSpaceId") Long groupSpaceId);
}
