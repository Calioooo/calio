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

  List<GroupMember> findByAccountIdAndStatusOrderByStatusChangedAtDescGroupSpaceIdDesc(
      Long accountId, GroupMemberStatus status);

  Optional<GroupMember> findByGroupSpaceIdAndAccountIdAndStatus(
      Long groupSpaceId, Long accountId, GroupMemberStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
            select member
            from GroupMember member
            where member.groupSpaceId = :groupSpaceId
              and member.accountId = :accountId
            """)
  Optional<GroupMember> findByGroupSpaceIdAndAccountIdForUpdate(
      @Param("groupSpaceId") Long groupSpaceId, @Param("accountId") Long accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
            select member
            from GroupMember member
            where member.groupSpaceId = :groupSpaceId
            order by member.id
            """)
  List<GroupMember> findAllByGroupSpaceIdForUpdateOrderById(
      @Param("groupSpaceId") Long groupSpaceId);

  int countByGroupSpaceIdAndStatus(Long groupSpaceId, GroupMemberStatus status);

  List<GroupMember> findAllByGroupSpaceIdAndStatus(Long groupSpaceId, GroupMemberStatus status);

  List<GroupMember> findAllByGroupSpaceIdInAndStatus(
      List<Long> groupSpaceIds, GroupMemberStatus status);

  @Query(
      """
            select member
            from GroupMember member
            where member.accountId = :accountId
              and member.groupSpaceId in :groupSpaceIds
              and member.status = com.calio.calendar.groupspace.domain.GroupMemberStatus.ACTIVE
            """)
  List<GroupMember> findAllActiveByAccountIdAndGroupSpaceIds(
      @Param("accountId") Long accountId, @Param("groupSpaceIds") List<Long> groupSpaceIds);

  @Query(
      """
            select case when count(member) > 0 then true else false end
            from GroupMember member
            where member.groupSpaceId = :groupSpaceId
              and member.status = com.calio.calendar.groupspace.domain.GroupMemberStatus.ACTIVE
              and lower(member.nickname) = lower(:nickname)
              and (:excludedMemberId is null or member.id <> :excludedMemberId)
            """)
  boolean hasActiveNicknameConflict(
      @Param("groupSpaceId") Long groupSpaceId,
      @Param("nickname") String nickname,
      @Param("excludedMemberId") Long excludedMemberId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from GroupMember member where member.groupSpaceId = :groupSpaceId")
  int deleteAllByGroupSpaceId(@Param("groupSpaceId") Long groupSpaceId);
}
