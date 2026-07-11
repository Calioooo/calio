package com.calio.calendar.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.testSecurityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class AuthenticatedAccountMockMvcTestConfig {

    @Bean
    MockMvcBuilderCustomizer authenticatedAccountDefaultRequest() {
        return builder -> builder.defaultRequest(get("/").with(testSecurityContext()));
    }
}
