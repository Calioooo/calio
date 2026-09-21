package com.calio.calendar.auth.controller;

import com.calio.calendar.auth.controller.dto.GuestAuthResponse;
import com.calio.calendar.auth.usecase.IssueGuestTokenUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final IssueGuestTokenUseCase issueGuestTokenUseCase;

  public AuthController(IssueGuestTokenUseCase issueGuestTokenUseCase) {
    this.issueGuestTokenUseCase = issueGuestTokenUseCase;
  }

  @PostMapping("/guest")
  public ResponseEntity<GuestAuthResponse> createGuestToken() {
    GuestAuthResponse response = issueGuestTokenUseCase.issue();
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
