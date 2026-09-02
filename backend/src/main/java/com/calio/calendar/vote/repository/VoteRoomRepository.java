package com.calio.calendar.vote.repository;

import com.calio.calendar.vote.domain.VoteRoom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRoomRepository extends JpaRepository<VoteRoom, Long> {

    @Query("select voteRoom from VoteRoom voteRoom where voteRoom.createdByAccount.id = :accountId order by voteRoom.id desc")
    List<VoteRoom> findAllByCreatedByAccountId(@Param("accountId") Long accountId);
}
