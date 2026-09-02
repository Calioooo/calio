package com.calio.calendar.vote.repository;

import com.calio.calendar.vote.domain.VoteParticipant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteParticipantRepository extends JpaRepository<VoteParticipant, Long> {

    @EntityGraph(attributePaths = "voteRoom")
    @Query("""
            select participant
            from VoteParticipant participant
            where participant.voteRoom.publicId = :voteRoomPublicId
              and lower(participant.nickname) = lower(:nickname)
            """)
    Optional<VoteParticipant> findByVoteRoomPublicIdAndNickname(
            @Param("voteRoomPublicId") UUID voteRoomPublicId,
            @Param("nickname") String nickname
    );
}
