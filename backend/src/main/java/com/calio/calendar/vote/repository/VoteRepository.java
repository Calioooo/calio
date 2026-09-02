package com.calio.calendar.vote.repository;

import com.calio.calendar.vote.domain.Vote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
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

    @EntityGraph(attributePaths = {"voteParticipant", "voteParticipant.voteRoom"})
    @Query("""
            select vote
            from Vote vote
            join vote.voteParticipant participant
            where participant.voteRoom.publicId = :voteRoomPublicId
              and participant.status = com.calio.calendar.vote.domain.VoteParticipantStatus.SUBMITTED
            order by vote.unavailableDate
            """)
    List<Vote> findAllSubmittedByVoteRoomPublicId(
            @Param("voteRoomPublicId") UUID voteRoomPublicId
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from Vote vote where vote.voteParticipant.id = :voteParticipantId")
    int deleteAllByVoteParticipantId(@Param("voteParticipantId") Long voteParticipantId);
}
