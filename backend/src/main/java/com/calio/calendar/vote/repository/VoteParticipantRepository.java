package com.calio.calendar.vote.repository;

import com.calio.calendar.vote.domain.VoteParticipant;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoteParticipantRepository extends JpaRepository<VoteParticipant, Long> {

  @Query(
      """
            select participant
            from VoteParticipant participant
            join VoteRoom voteRoom on participant.voteRoomId = voteRoom.id
            where voteRoom.publicId = :voteRoomPublicId
              and lower(participant.nickname.value) = lower(:nickname)
            """)
  Optional<VoteParticipant> findByVoteRoomPublicIdAndNickname(
      @Param("voteRoomPublicId") UUID voteRoomPublicId, @Param("nickname") String nickname);

  @Query(
      """
            select participant
            from VoteParticipant participant
            join VoteRoom voteRoom on participant.voteRoomId = voteRoom.id
            where voteRoom.publicId = :voteRoomPublicId
              and participant.accountId = :accountId
            """)
  Optional<VoteParticipant> findByVoteRoomPublicIdAndAccountId(
      @Param("voteRoomPublicId") UUID voteRoomPublicId, @Param("accountId") Long accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
            select participant
            from VoteParticipant participant
            join VoteRoom voteRoom on participant.voteRoomId = voteRoom.id
            where voteRoom.publicId = :voteRoomPublicId
              and lower(participant.nickname.value) = lower(:nickname)
            """)
  Optional<VoteParticipant> findByVoteRoomPublicIdAndNicknameForUpdate(
      @Param("voteRoomPublicId") UUID voteRoomPublicId, @Param("nickname") String nickname);

  @Query(
      """
            select participant.nickname.value
            from VoteParticipant participant
            join VoteRoom voteRoom on participant.voteRoomId = voteRoom.id
            where voteRoom.publicId = :voteRoomPublicId
              and participant.status = com.calio.calendar.vote.domain.VoteParticipantStatus.SUBMITTED
            """)
  List<String> findSubmittedNicknamesByVoteRoomPublicId(
      @Param("voteRoomPublicId") UUID voteRoomPublicId);
}
