package com.calio.calendar.vote.repository;

import com.calio.calendar.vote.domain.VoteRoom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteRoomRepository extends JpaRepository<VoteRoom, Long> {
    List<VoteRoom> findByCreatedByAccount_IdOrderByIdDesc(Long accountId);
}
