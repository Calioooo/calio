package com.calio.calendar.vote.controller;

import com.calio.calendar.vote.controller.dto.CreateVoteParticipantRequest;
import com.calio.calendar.vote.controller.dto.SubmitVoteRequest;
import com.calio.calendar.vote.controller.dto.VoteParticipantResponse;
import com.calio.calendar.vote.controller.dto.VoteSubmissionResponse;
import com.calio.calendar.vote.service.VoteParticipantService;
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

    private final VoteParticipantService voteParticipantService;

    public VoteParticipantController(VoteParticipantService voteParticipantService) {
        this.voteParticipantService = voteParticipantService;
    }

    @PostMapping("/participants")
    public ResponseEntity<VoteParticipantResponse> create(
            @PathVariable UUID publicId,
            @Valid @RequestBody CreateVoteParticipantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(voteParticipantService.create(publicId, request));
    }

    @PutMapping("/votes")
    public VoteSubmissionResponse submitVotes(
            @PathVariable UUID publicId,
            @Valid @RequestBody SubmitVoteRequest request
    ) {
        return voteParticipantService.submitVotes(publicId, request);
    }
}
