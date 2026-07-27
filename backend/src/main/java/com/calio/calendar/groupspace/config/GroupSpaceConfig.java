package com.calio.calendar.groupspace.config;

import java.security.SecureRandom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GroupSpaceConfig {

    @Bean
    public SecureRandom groupSpaceSecureRandom() {
        return new SecureRandom();
    }
}
