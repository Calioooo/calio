package com.calio.calendar.auth;

import com.calio.calendar.auth.dto.GuestAuthResponse;
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

    @PostMapping("/guest")
    public ResponseEntity<GuestAuthResponse> createGuestToken() {
        GuestAuthResponse response = authService.issueGuestToken();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
