package com.calio.calendar.groupspace.repository;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupSpace_IdAndAccount_Id(Long groupSpaceId, Long accountId);

    Optional<GroupMember> findByGroupSpace_IdAndAccount_IdAndStatus(
            Long groupSpaceId,
            Long accountId,
            GroupMemberStatus status
    );

    Optional<GroupMember> findByIdAndGroupSpace_Id(Long id, Long groupSpaceId);

    List<GroupMember> findByGroupSpace_IdAndStatus(Long groupSpaceId, GroupMemberStatus status);

    List<GroupMember> findByGroupSpace_IdOrderByIdAsc(Long groupSpaceId);

    long countByGroupSpace_IdAndStatus(Long groupSpaceId, GroupMemberStatus status);

    @Query("""
            select member
            from GroupMember member
            join fetch member.groupSpace groupSpace
            where member.account.id = :accountId and member.status = :status
            order by member.updatedAt desc, groupSpace.id desc
            """)
    List<GroupMember> findVisibleGroups(
            @Param("accountId") Long accountId,
            @Param("status") GroupMemberStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select member
            from GroupMember member
            where member.groupSpace.id = :groupSpaceId and member.id in :memberIds
            order by member.id asc
            """)
    List<GroupMember> findAllByIdForUpdate(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("memberIds") Collection<Long> memberIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select member
            from GroupMember member
            where member.groupSpace.id = :groupSpaceId and member.account.id = :accountId
            """)
    Optional<GroupMember> findByAccountForUpdate(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("accountId") Long accountId
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from GroupMember member where member.groupSpace.id = :groupSpaceId")
    int deleteByGroupSpace_Id(@Param("groupSpaceId") Long groupSpaceId);
}
