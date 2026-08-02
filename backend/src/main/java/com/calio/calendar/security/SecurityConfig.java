package com.calio.calendar.security;

import com.calio.calendar.common.error.ErrorCode;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/guest").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/national-holidays").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/group-invitations/preview").authenticated()
                        .requestMatchers("/api/events").authenticated()
                        .requestMatchers("/api/events/**").authenticated()
                        .requestMatchers("/api/recurrence-events").authenticated()
                        .requestMatchers("/api/recurrence-events/**").authenticated()
                        .requestMatchers("/api/tasks").authenticated()
                        .requestMatchers("/api/tasks/**").authenticated()
                        .requestMatchers("/api/tags").authenticated()
                        .requestMatchers("/api/custom-tags").authenticated()
                        .requestMatchers("/api/custom-tags/**").authenticated()
                        .requestMatchers("/api/integrations/**").authenticated()
                        .requestMatchers("/api/group-spaces").authenticated()
                        .requestMatchers("/api/group-spaces/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/group-invitations/accept").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(exception -> exception.authenticationEntryPoint(
                        (request, response, authException) ->
                                authenticationErrorResponseWriter.write(request, response, ErrorCode.AUTH_TOKEN_REQUIRED)
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
