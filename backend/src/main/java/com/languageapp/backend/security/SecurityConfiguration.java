package com.languageapp.backend.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Core security configuration for the application.
 * <p>
 * Defines HTTP security rules, CORS policies, session management (stateless),
 * and wires up the authentication providers and JWT filters.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Configures the main security filter chain.
     *
     * @param http the {@link HttpSecurity} object to configure
     * @return the built {@link SecurityFilterChain}
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Initializing Security Filter Chain...");

        http
                // Enable CORS with custom configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Disable CSRF since we use stateless JWT authentication
                .csrf(AbstractHttpConfigurer::disable)

                // Define authorization rules for HTTP requests
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll() // Public endpoints (login, register)
                        .anyRequest().authenticated() // All other endpoints require valid JWT
                )

                // Enforce stateless session management (no server-side HTTP sessions)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Register our custom authentication provider
                .authenticationProvider(authenticationProvider())

                // Insert the JWT filter before the standard Spring UsernamePassword filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configures Cross-Origin Resource Sharing (CORS).
     * Allows the React frontend to securely communicate with the API
     * and ensures that HttpOnly cookies (Refresh Token) are accepted.
     *
     * @return the configured {@link CorsConfigurationSource}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origin patterns
        configuration.setAllowedOriginPatterns(List.of("http://localhost:3000", "http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // Must be true for the frontend to receive/send HttpOnly cookies
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Configures the Data Access Object (DAO) authentication provider.
     * Links the custom UserDetailsService and the BCrypt password encoder.
     *
     * @return the configured {@link AuthenticationProvider}
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Exposes the {@link AuthenticationManager} as a Spring bean.
     * Used by the AuthenticationService to authenticate users during login.
     *
     * @param config the authentication configuration
     * @return the authentication manager
     * @throws Exception if an error occurs while retrieving the manager
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Configures the password hashing algorithm.
     * Uses BCrypt as strictly required by the project specifications.
     *
     * @return the {@link PasswordEncoder}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}