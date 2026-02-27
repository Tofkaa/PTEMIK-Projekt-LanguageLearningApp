package com.languageapp.backend.security;

import io.jsonwebtoken.JwtException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security filter that intercepts all incoming HTTP requests to validate JWT access tokens.
 * <p>
 * Ensures that requests to protected endpoints contain a valid Bearer token.
 * If valid, it populates the Spring Security context with the authenticated user's details.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // 1. Skip filtering if the Authorization header is missing or does not start with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract the raw JWT from the header
        final String jwt = authHeader.substring(7);

        try {
            // 2. Extract the username from the token
            // This might throw a JwtException if the token is expired or malformed
            final String userEmail = jwtService.extractUsername(jwt);

            // 3. If a username is found and the user is not already authenticated in the current context
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Load user details from the database
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                // 4. Validate the token against the loaded user details
                if (jwtService.isTokenValid(jwt, userDetails)) {

                    log.debug("JWT token successfully validated for user: {}", userEmail);

                    // 5. Create a trusted Spring Security authentication token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    // Add request details (e.g., IP address, session ID) to the auth token
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 6. Set the authentication in the Security Context
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException e) {
            // Catching JWT parsing/validation exceptions gracefully
            log.warn("JWT Authentication failed for request {}: {}", request.getRequestURI(), e.getMessage());
            // We do NOT set the authentication. The request will proceed as anonymous,
            // and Spring Security will natively block access to protected endpoints.
        } catch (Exception e) {
            log.error("An unexpected error occurred during JWT authentication filtering", e);
        }

        // 7. Continue the filter chain execution
        filterChain.doFilter(request, response);
    }
}