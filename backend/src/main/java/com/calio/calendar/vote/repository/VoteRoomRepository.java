package com.calio.calendar.vote.repository;

import com.calio.calendar.vote.domain.VoteRoom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRoomRepository extends JpaRepository<VoteRoom, Long> {

    @Query("select voteRoom from VoteRoom voteRoom where voteRoom.createdByAccount.id = :accountId order by voteRoom.id desc")
    List<VoteRoom> findAllByCreatedByAccountId(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select voteRoom
            from VoteRoom voteRoom
            where voteRoom.publicId = :publicId
            """)
    Optional<VoteRoom> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
}
