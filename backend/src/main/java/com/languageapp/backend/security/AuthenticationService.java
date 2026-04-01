package com.languageapp.backend.security;

import com.languageapp.backend.dto.request.LoginRequest;
import com.languageapp.backend.dto.request.RegisterRequest;
import com.languageapp.backend.dto.response.AuthResponse;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service responsible for user authentication and registration logic.
 * <p>
 * Handles secure password hashing, user verification, and the generation
 * of both access and refresh tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    /**
     * Internal record to transport the response payload and the HTTP-only refresh token
     * back to the controller.
     */
    public record AuthResult(AuthResponse responseDto, String refreshToken) {}

    /**
     * Registers a new user account in the system.
     * <p>
     * Note: All public registrations are strictly assigned the 'STUDENT' role
     * to prevent Privilege Escalation (Mass Assignment) vulnerabilities.
     *
     * @param request registration details provided by the client
     * @return {@link AuthResult} containing the generated tokens and user info
     * @throws BadRequestException if the email is already registered
     */
    @Transactional
    public AuthResult register(RegisterRequest request) {
        log.info("Attempting to register new user with email: {}", request.getEmail());

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("Registration failed: Email already exists - {}", request.getEmail());
            throw new BadRequestException("This email already exists!");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());

        String requestedRole = request.getRole() != null ? request.getRole().trim().toUpperCase() : "";

        if ("TEACHER".equals(requestedRole)) {
            user.setRole("TEACHER");
            log.info("Registering TEACHER user with email: {}...", request.getEmail());
        } else {
            user.setRole("STUDENT");
            log.info("Registering STUDENT user with email: {}...", request.getEmail());
        }

        userRepository.save(user);
        log.info("User successfully saved to database with ID: {}", user.getUserId());

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(user.getRole())
                .build();

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String jwtToken = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(user.getUserId());

        AuthResponse response = new AuthResponse(
                jwtToken,
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );

        log.info("Tokens successfully generated for user: {}", user.getEmail());
        return new AuthResult(response, refreshToken);
    }

    /**
     * Authenticates an existing user and issues new tokens.
     *
     * @param request login credentials provided by the client
     * @return {@link AuthResult} containing the generated tokens and user info
     * @throws BadRequestException if the user is not found in the database
     */
    @Transactional
    public AuthResult authenticate(LoginRequest request) {
        log.info("Authentication attempt for email: {}", request.getEmail());


        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.error("Authenticated user not found in database: {}", request.getEmail());
                    return new BadRequestException("Invalid login credentials.");
                });

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        String jwtToken = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.createRefreshToken(user.getUserId());

        AuthResponse response = new AuthResponse(
                jwtToken,
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );

        log.info("User successfully authenticated: {}", user.getEmail());
        return new AuthResult(response, refreshToken);
    }

    /**
     * Deletes the refresh token if a user requests to log out.
     * @param rawRefreshToken the refresh token to be deleted.
     */
    @Transactional
    public String logout(String rawRefreshToken) {
        return refreshTokenService.deleteByRawToken(rawRefreshToken);
    }

    /**
     * Generates a new Access Token using a valid Refresh Token.
     * * @param rawRefreshToken the raw refresh token string from the HttpOnly cookie
     * @return {@link AuthResponse} containing the new JWT access token
     * @throws BadRequestException if the refresh token is invalid or expired
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String rawRefreshToken) {
        log.info("Attempting to refresh access token...");

        return refreshTokenService.findByRawToken(rawRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(com.languageapp.backend.entity.RefreshToken::getUser)
                .map(user -> {
                    UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                            .username(user.getEmail())
                            .password(user.getPasswordHash())
                            .authorities(user.getRole())
                            .build();

                    String newAccessToken = jwtService.generateToken(userDetails);
                    log.info("Access token successfully refreshed for user: {}", user.getEmail());

                    return new AuthResponse(
                            newAccessToken,
                            user.getUserId(),
                            user.getName(),
                            user.getEmail(),
                            user.getRole()
                    );
                })
                .orElseThrow(() -> {
                    log.warn("Refresh token validation failed: Token not found or invalid hash.");
                    return new BadRequestException("An error occurred, please login again.");
                });
    }

}