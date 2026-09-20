package com.calio.calendar.vote.controller;

import com.calio.calendar.vote.controller.dto.CreateVoteParticipantRequest;
import com.calio.calendar.vote.controller.dto.LookupVoteParticipantSelectionRequest;
import com.calio.calendar.vote.controller.dto.SubmitVoteRequest;
import com.calio.calendar.vote.controller.dto.VoteParticipantResponse;
import com.calio.calendar.vote.controller.dto.VoteParticipantSelectionResponse;
import com.calio.calendar.vote.controller.dto.VoteSubmissionResponse;
import com.calio.calendar.vote.usecase.CreateVoteParticipantUseCase;
import com.calio.calendar.vote.usecase.LookupVoteParticipantSelectionUseCase;
import com.calio.calendar.vote.usecase.SubmitVoteUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vote-rooms/{publicId}")
public class VoteParticipantController {

  private final CreateVoteParticipantUseCase createVoteParticipantUseCase;
  private final SubmitVoteUseCase submitVoteUseCase;
  private final LookupVoteParticipantSelectionUseCase lookupVoteParticipantSelectionUseCase;

  public VoteParticipantController(
      CreateVoteParticipantUseCase createVoteParticipantUseCase,
      SubmitVoteUseCase submitVoteUseCase,
      LookupVoteParticipantSelectionUseCase lookupVoteParticipantSelectionUseCase) {
    this.createVoteParticipantUseCase = createVoteParticipantUseCase;
    this.submitVoteUseCase = submitVoteUseCase;
    this.lookupVoteParticipantSelectionUseCase = lookupVoteParticipantSelectionUseCase;
  }

  @PostMapping("/participants")
  public ResponseEntity<VoteParticipantResponse> create(
      @PathVariable UUID publicId, @Valid @RequestBody CreateVoteParticipantRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(createVoteParticipantUseCase.create(publicId, request.nickname(), request.password()));
  }

  @PutMapping("/votes")
  public VoteSubmissionResponse submitVotes(
      @PathVariable UUID publicId, @Valid @RequestBody SubmitVoteRequest request) {
    return submitVoteUseCase.submit(
        publicId, request.nickname(), request.password(), request.unavailableDates());
  }

  @PostMapping("/votes/lookup")
  public VoteParticipantSelectionResponse lookupSelection(
      @PathVariable UUID publicId,
      @Valid @RequestBody LookupVoteParticipantSelectionRequest request) {
    return lookupVoteParticipantSelectionUseCase.lookup(publicId, request.nickname(), request.password());
  }
}
