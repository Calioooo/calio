package com.calio.calendar.groupinvitation.config;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GroupInvitationConfig {

    @Bean
    public SecureRandom groupInvitationSecureRandom() {
        return new SecureRandom();
    }
}
