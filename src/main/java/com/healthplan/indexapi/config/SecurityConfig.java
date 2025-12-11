package com.healthplan.indexapi.config;

import com.healthplan.indexapi.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Security filter chain
        http
            // Disable Cross-Site Request Forgery (CSRF) protection since this is a stateless API.
            .csrf(csrf -> csrf.disable())

            // Configure session management to be stateless, relying only on the JWT token.
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Define authorization rules, requiring authentication for all incoming requests.
            .authorizeHttpRequests(auth -> auth
                    .anyRequest().authenticated())

            // Insert the custom JWT filter BEFORE the traditional form login filter to handle token authentication first.
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
