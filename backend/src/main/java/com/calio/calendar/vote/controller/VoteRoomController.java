package com.calio.calendar.vote.controller;

import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.vote.controller.dto.CreateVoteRoomRequest;
import com.calio.calendar.vote.controller.dto.VoteResultResponse;
import com.calio.calendar.vote.controller.dto.VoteRoomResponse;
import com.calio.calendar.vote.usecase.CreateVoteRoomUseCase;
import com.calio.calendar.vote.usecase.GetVoteResultUseCase;
import com.calio.calendar.vote.usecase.ListMyVoteRoomsUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vote-rooms")
public class VoteRoomController {
  private final CreateVoteRoomUseCase createVoteRoomUseCase;
  private final ListMyVoteRoomsUseCase listMyVoteRoomsUseCase;
  private final GetVoteResultUseCase getVoteResultUseCase;

  public VoteRoomController(
      CreateVoteRoomUseCase createVoteRoomUseCase,
      ListMyVoteRoomsUseCase listMyVoteRoomsUseCase,
      GetVoteResultUseCase getVoteResultUseCase) {
    this.createVoteRoomUseCase = createVoteRoomUseCase;
    this.listMyVoteRoomsUseCase = listMyVoteRoomsUseCase;
    this.getVoteResultUseCase = getVoteResultUseCase;
  }

  @PostMapping
  public ResponseEntity<VoteRoomResponse> create(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody CreateVoteRoomRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            createVoteRoomUseCase.create(
                account.accountId(), request.name(), request.candidateEndDate()));
  }

  @GetMapping("/me")
  public List<VoteRoomResponse> listMine(@AuthenticationPrincipal AuthenticatedAccount account) {
    return listMyVoteRoomsUseCase.list(account.accountId());
  }

  @GetMapping("/{publicId}")
  public VoteResultResponse getResult(@PathVariable java.util.UUID publicId) {
    return getVoteResultUseCase.get(publicId);
  }
}
