package com.calio.calendar.security;

import com.calio.calendar.exception.ErrorCode;
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
    private final AuthenticationErrorResponseWriter authenticationErrorResponseWriter;

    public SecurityConfig(
            BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
            AuthenticationErrorResponseWriter authenticationErrorResponseWriter
    ) {
        this.bearerTokenAuthenticationFilter = bearerTokenAuthenticationFilter;
        this.authenticationErrorResponseWriter = authenticationErrorResponseWriter;
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
                        .requestMatchers("/api/events").authenticated()
                        .requestMatchers("/api/events/**").authenticated()
                        .requestMatchers("/api/recurrence-events").authenticated()
                        .requestMatchers("/api/recurrence-events/**").authenticated()
                        .requestMatchers("/api/tasks").authenticated()
                        .requestMatchers("/api/tasks/**").authenticated()
                        .requestMatchers("/api/tags").authenticated()
                        .requestMatchers("/api/custom-tags").authenticated()
                        .requestMatchers("/api/custom-tags/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exception -> exception.authenticationEntryPoint(
                        (request, response, authException) ->
                                authenticationErrorResponseWriter.write(response, ErrorCode.AUTH_TOKEN_REQUIRED)
                ))
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
