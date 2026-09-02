package com.calio.calendar.vote.repository;

import com.calio.calendar.vote.domain.Vote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    @Query("""
            select vote
            from Vote vote
            where vote.voteParticipant.id = :voteParticipantId
            order by vote.unavailableDate
            """)
    List<Vote> findAllByVoteParticipantId(@Param("voteParticipantId") Long voteParticipantId);
}
