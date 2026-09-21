package com.calio.calendar.vote.controller;

import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.vote.controller.dto.CreateMyVoteParticipantRequest;
import com.calio.calendar.vote.controller.dto.CreateVoteParticipantRequest;
import com.calio.calendar.vote.controller.dto.LookupVoteParticipantSelectionRequest;
import com.calio.calendar.vote.controller.dto.SubmitMyVoteRequest;
import com.calio.calendar.vote.controller.dto.SubmitVoteRequest;
import com.calio.calendar.vote.controller.dto.VoteParticipantResponse;
import com.calio.calendar.vote.controller.dto.VoteParticipantSelectionResponse;
import com.calio.calendar.vote.controller.dto.VoteSubmissionResponse;
import com.calio.calendar.vote.usecase.CreateMyVoteParticipantUseCase;
import com.calio.calendar.vote.usecase.CreateVoteParticipantUseCase;
import com.calio.calendar.vote.usecase.LookupMyVoteParticipantSelectionUseCase;
import com.calio.calendar.vote.usecase.LookupVoteParticipantSelectionUseCase;
import com.calio.calendar.vote.usecase.SubmitMyVoteUseCase;
import com.calio.calendar.vote.usecase.SubmitVoteUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
  private final CreateMyVoteParticipantUseCase createMyVoteParticipantUseCase;
  private final SubmitVoteUseCase submitVoteUseCase;
  private final SubmitMyVoteUseCase submitMyVoteUseCase;
  private final LookupVoteParticipantSelectionUseCase lookupVoteParticipantSelectionUseCase;
  private final LookupMyVoteParticipantSelectionUseCase lookupMyVoteParticipantSelectionUseCase;

  public VoteParticipantController(
      CreateVoteParticipantUseCase createVoteParticipantUseCase,
      CreateMyVoteParticipantUseCase createMyVoteParticipantUseCase,
      SubmitVoteUseCase submitVoteUseCase,
      SubmitMyVoteUseCase submitMyVoteUseCase,
      LookupVoteParticipantSelectionUseCase lookupVoteParticipantSelectionUseCase,
      LookupMyVoteParticipantSelectionUseCase lookupMyVoteParticipantSelectionUseCase) {
    this.createVoteParticipantUseCase = createVoteParticipantUseCase;
    this.createMyVoteParticipantUseCase = createMyVoteParticipantUseCase;
    this.submitVoteUseCase = submitVoteUseCase;
    this.submitMyVoteUseCase = submitMyVoteUseCase;
    this.lookupVoteParticipantSelectionUseCase = lookupVoteParticipantSelectionUseCase;
    this.lookupMyVoteParticipantSelectionUseCase = lookupMyVoteParticipantSelectionUseCase;
  }

  @PostMapping("/participants")
  public ResponseEntity<VoteParticipantResponse> create(
      @PathVariable UUID publicId, @Valid @RequestBody CreateVoteParticipantRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            createVoteParticipantUseCase.create(publicId, request.nickname(), request.password()));
  }

  @PostMapping("/participants/me")
  public ResponseEntity<VoteParticipantResponse> createMine(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody CreateMyVoteParticipantRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            createMyVoteParticipantUseCase.create(
                publicId, account.accountId(), request.nickname()));
  }

  @PutMapping("/votes")
  public VoteSubmissionResponse submitVotes(
      @PathVariable UUID publicId, @Valid @RequestBody SubmitVoteRequest request) {
    return submitVoteUseCase.submit(
        publicId, request.nickname(), request.password(), request.unavailableDates());
  }

  @PutMapping("/participants/me/votes")
  public VoteSubmissionResponse submitMyVotes(
      @PathVariable UUID publicId,
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody SubmitMyVoteRequest request) {
    return submitMyVoteUseCase.submit(publicId, account.accountId(), request.unavailableDates());
  }

  @PostMapping("/votes/lookup")
  public VoteParticipantSelectionResponse lookupSelection(
      @PathVariable UUID publicId,
      @Valid @RequestBody LookupVoteParticipantSelectionRequest request) {
    return lookupVoteParticipantSelectionUseCase.lookup(
        publicId, request.nickname(), request.password());
  }

  @GetMapping("/participants/me")
  public VoteParticipantSelectionResponse lookupMySelection(
      @PathVariable UUID publicId, @AuthenticationPrincipal AuthenticatedAccount account) {
    return lookupMyVoteParticipantSelectionUseCase.lookup(publicId, account.accountId());
  }
}
