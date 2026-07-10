package com.calio.calendar.auth;

import com.calio.calendar.auth.dto.AnonymousAuthResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/anonymous")
    public ResponseEntity<AnonymousAuthResponse> createAnonymousToken() {
        AnonymousAuthResponse response = authService.issueAnonymousToken();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
