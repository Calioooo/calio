package com.calio.calendar.vote.repository;

import com.calio.calendar.vote.domain.VoteRoom;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRoomRepository extends JpaRepository<VoteRoom, Long> {

  List<VoteRoom> findByCreatedByAccountIdOrderByIdDesc(Long accountId);

  Optional<VoteRoom> findByPublicId(UUID publicId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
            delete from VoteRoom voteRoom
            where voteRoom.candidateDateRange.candidateEndDate < :cutoffDate
            """)
  int deleteExpiredVoteRoomsBefore(@Param("cutoffDate") LocalDate cutoffDate);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<VoteRoom> findForUpdateByPublicId(UUID publicId);
}
