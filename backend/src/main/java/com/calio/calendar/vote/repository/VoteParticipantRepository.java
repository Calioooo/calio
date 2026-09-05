package com.calio.calendar.vote.repository;

import com.calio.calendar.vote.domain.VoteParticipant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "voteRoom")
    @Query("""
            select participant
            from VoteParticipant participant
            where participant.voteRoom.publicId = :voteRoomPublicId
              and lower(participant.nickname) = lower(:nickname)
            """)
    Optional<VoteParticipant> findByVoteRoomPublicIdAndNicknameForUpdate(
            @Param("voteRoomPublicId") UUID voteRoomPublicId,
            @Param("nickname") String nickname
    );

    @Query("""
            select participant.nickname
            from VoteParticipant participant
            where participant.voteRoom.publicId = :voteRoomPublicId
              and participant.status = com.calio.calendar.vote.domain.VoteParticipantStatus.SUBMITTED
            """)
    List<String> findSubmittedNicknamesByVoteRoomPublicId(
            @Param("voteRoomPublicId") UUID voteRoomPublicId
    );
}
