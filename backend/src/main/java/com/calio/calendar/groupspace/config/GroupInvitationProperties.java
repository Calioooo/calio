package com.calio.calendar.groupspace.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Arrays;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "group-space.invitation")
public class GroupInvitationProperties {

    private final Environment environment;
    private String baseUrl = "https://calio.app/invite";

    public GroupInvitationProperties(Environment environment) {
        this.environment = environment;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String buildInviteUrl(String linkToken) {
        return normalizedBaseUrl() + "/" + linkToken;
    }

    @PostConstruct
    void validate() {
        normalizedBaseUrl();
    }

    private String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw invalidBaseUrl();
        }
        URI uri = parse(baseUrl);
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null) {
            throw invalidBaseUrl();
        }
        if (isProduction() && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw invalidBaseUrl();
        }
        String normalized = uri.toString();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private URI parse(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw invalidBaseUrl();
        }
    }

    private boolean isProduction() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod")
                        || profile.equalsIgnoreCase("production"));
    }

    private IllegalStateException invalidBaseUrl() {
        return new IllegalStateException("Invalid group-space invitation base URL");
    }
}
