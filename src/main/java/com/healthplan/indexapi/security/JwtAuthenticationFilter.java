package com.healthplan.indexapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Used by SecurityConfig.java through injection
 * Custom security filter responsible for intercepting all HTTP requests to extract and validate the JWT (JSON Web Token)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenValidator tokenValidator;

    /**
     * Automatically executed by Spring Security.
     * Extracts the token, validates it using TokenValidator, and sets authenticated principal into SecurityContextHolder
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: Missing or invalid token");
            return;
        }

        String token = authHeader.substring(7);

        if (!tokenValidator.validateToken(token)) {
            log.warn("Token validation failed");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: Invalid token");
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "authenticated-user",  // principal
                        null,                   // credentials: null bc token is verified
                        Collections.emptyList() // authorities: could have any roles
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info("Request authenticated successfully");
        filterChain.doFilter(request, response);
    }
}
