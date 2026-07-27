package com.calio.calendar.groupspace.repository;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupSpaceIdAndAccountIdAndStatus(
            Long groupSpaceId,
            Long accountId,
            GroupMemberStatus status
    );

    @Query("""
            select member
            from GroupMember member
            where member.accountId = :accountId
              and member.status = :status
            order by member.updatedAt desc, member.groupSpaceId desc
            """)
    List<GroupMember> findMembershipsForList(
            @Param("accountId") Long accountId,
            @Param("status") GroupMemberStatus status
    );

    int countByGroupSpaceIdAndStatus(Long groupSpaceId, GroupMemberStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select member
            from GroupMember member
            where member.groupSpaceId = :groupSpaceId
            order by member.id
            """)
    List<GroupMember> findAllByGroupSpaceIdForUpdate(@Param("groupSpaceId") Long groupSpaceId);
}
