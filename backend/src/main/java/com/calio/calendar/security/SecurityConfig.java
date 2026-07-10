package com.calio.calendar.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter;

    public SecurityConfig(BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter) {
        this.bearerTokenAuthenticationFilter = bearerTokenAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/anonymous").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/national-holidays").permitAll()
                        .requestMatchers("/api/events").permitAll()
                        .requestMatchers("/api/events/**").permitAll()
                        .requestMatchers("/api/recurrence-events").permitAll()
                        .requestMatchers("/api/recurrence-events/**").permitAll()
                        .requestMatchers("/api/tasks").permitAll()
                        .requestMatchers("/api/tasks/**").permitAll()
                        .requestMatchers("/api/tags").permitAll()
                        .requestMatchers("/api/custom-tags").permitAll()
                        .requestMatchers("/api/custom-tags/**").permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public FilterRegistrationBean<BearerTokenAuthenticationFilter> bearerTokenFilterRegistration() {
        FilterRegistrationBean<BearerTokenAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(bearerTokenAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }
}
