package com.calio.calendar.vote.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.CreateVoteRoomRequest;
import com.calio.calendar.vote.controller.dto.VoteRoomResponse;
import com.calio.calendar.vote.domain.VoteRoom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteRoomService {
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_CANDIDATE_DAYS = 31;
    private final VoteRoomQueryService voteRoomQueryService;
    private final VoteRoomCommandService voteRoomCommandService;
    private final AccountQueryService accountQueryService;
    private final Clock clock;

    public VoteRoomService(VoteRoomQueryService voteRoomQueryService, VoteRoomCommandService voteRoomCommandService, AccountQueryService accountQueryService, Clock clock) {
        this.voteRoomQueryService = voteRoomQueryService;
        this.voteRoomCommandService = voteRoomCommandService;
        this.accountQueryService = accountQueryService;
        this.clock = clock;
    }

    @Transactional
    public VoteRoomResponse create(Long accountId, CreateVoteRoomRequest request) {
        LocalDate start = LocalDate.now(clock.withZone(KOREA_ZONE));
        if (request.candidateEndDate().isBefore(start) || request.candidateEndDate().isAfter(start.plusDays(MAX_CANDIDATE_DAYS - 1))) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
        Account account = accountQueryService.getAccount(accountId);
        VoteRoom voteRoom = voteRoomCommandService.create(new VoteRoom(UUID.randomUUID(), request.name(), start, request.candidateEndDate(), account));
        return VoteRoomResponse.from(voteRoom);
    }

    public List<VoteRoomResponse> listMine(Long accountId) {
        return voteRoomQueryService.listByCreatedByAccountId(accountId).stream().map(VoteRoomResponse::from).toList();
    }
}
