package com.calio.calendar.groupspace.repository;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    @Query("""
            select member
            from GroupMember member
            join fetch member.groupSpace groupSpace
            where member.accountId = :accountId
              and member.status = :status
            order by member.updatedAt desc, groupSpace.id desc
            """)
    List<GroupMember> findByAccountIdAndStatusOrderByUpdatedAtDesc(
            @Param("accountId") Long accountId,
            @Param("status") GroupMemberStatus status
    );

    @Query("""
            select member
            from GroupMember member
            join fetch member.groupSpace groupSpace
            where groupSpace.id = :groupSpaceId
              and member.accountId = :accountId
              and member.status = :status
            """)
    Optional<GroupMember> findByGroupSpaceIdAndAccountIdAndStatus(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("accountId") Long accountId,
            @Param("status") GroupMemberStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select member
            from GroupMember member
            where member.groupSpace.id = :groupSpaceId
              and member.accountId = :accountId
            """)
    Optional<GroupMember> findByGroupSpaceIdAndAccountIdForUpdate(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("accountId") Long accountId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select member
            from GroupMember member
            where member.groupSpace.id = :groupSpaceId
            order by member.id
            """)
    List<GroupMember> findAllByGroupSpaceIdForUpdateOrderById(
            @Param("groupSpaceId") Long groupSpaceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select member
            from GroupMember member
            where member.groupSpace.id = :groupSpaceId
              and member.id = :memberId
            """)
    Optional<GroupMember> findByGroupSpaceIdAndIdForUpdate(
            @Param("groupSpaceId") Long groupSpaceId,
            @Param("memberId") Long memberId
    );

    int countByGroupSpace_IdAndStatus(Long groupSpaceId, GroupMemberStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GroupMember member where member.groupSpace.id = :groupSpaceId")
    int deleteAllByGroupSpaceId(@Param("groupSpaceId") Long groupSpaceId);
}
