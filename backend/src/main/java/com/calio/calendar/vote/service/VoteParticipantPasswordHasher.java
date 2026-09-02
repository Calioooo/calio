package com.calio.calendar.vote.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class VoteParticipantPasswordHasher {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String hash(String password) {
        return passwordEncoder.encode(password);
    }

    public boolean matches(String password, String passwordHash) {
        return passwordEncoder.matches(password, passwordHash);
    }
}
