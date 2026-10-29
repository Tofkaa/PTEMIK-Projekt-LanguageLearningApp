package com.languageapp.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ApiRequestLimiter requestLimiter;
    private final com.languageapp.backend.repository.UserRepository accessUsers;
    private final ClientAddressResolver clientAddresses;
    private final BrowserOriginPolicy browserOriginPolicy;
    private final CustomUserDetailsService userDetailsService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final java.time.Clock clock;
    private final OAuthLinkService oauthLinks;
    private final org.springframework.security.oauth2.client.registration.ClientRegistrationRepository registrations;

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

        OAuthLinkAuthorization linking = new OAuthLinkAuthorization(registrations, oauthLinks);
        http
                // Enable CORS with custom configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Bearer APIs do not authenticate with cookies. Cookie-issuing/consuming APIs
                // instead require the configured Origin via BrowserOriginFilter; OAuth GET callbacks use state.
                .csrf(AbstractHttpConfigurer::disable)

                // Define authorization rules for HTTP requests
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/auth/**").permitAll() // Public endpoints (login, register)
                        .anyRequest().authenticated() // All other endpoints require valid JWT
                )

                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(linking).authorizationRequestRepository(linking))
                        .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(linking))
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler)
                )

                // API authentication is stateless; OAuth state/handoff intentionally uses a temporary HTTP session.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.warn("http.unauthorized");
                            com.languageapp.backend.observability.ApiErrors.write(request,response,401,"Unauthorized");
                        })
                        .accessDeniedHandler((request,response,denied)->com.languageapp.backend.observability.ApiErrors.write(request,response,403,"Nincs jogosultságod ehhez a művelethez."))
                )

                // Register our custom authentication provider
                .authenticationProvider(authenticationProvider())

                // Insert the JWT filter before the standard Spring UsernamePassword filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new BrowserOriginFilter(browserOriginPolicy), JwtAuthenticationFilter.class)
                .addFilterAfter(new AccountVerificationFilter(accessUsers, objectMapper, clock), org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .addFilterAfter(new ApiRateLimitFilter(requestLimiter, clientAddresses, objectMapper, clock), org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .addFilterAfter(new AnswerPayloadFilter(objectMapper, clock), org.springframework.security.web.access.intercept.AuthorizationFilter.class);

        http.oauth2Login(oauth2 -> oauth2.addObjectPostProcessor(
                new org.springframework.security.config.ObjectPostProcessor<org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter>() {
                    @Override public <O extends org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter> O postProcess(O filter) {
                        filter.setAuthenticationFailureHandler(oAuth2AuthenticationFailureHandler);
                        return filter;
                    }
                }));
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

        // One explicit frontend origin, shared with the cookie API CSRF policy.
        configuration.setAllowedOrigins(List.of(browserOriginPolicy.origin()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // Must be true for the frontend to receive/send HttpOnly cookies
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Retry-After", "X-Request-ID"));

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
