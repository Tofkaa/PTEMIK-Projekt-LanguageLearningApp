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

        user.setRole("STUDENT");

        userRepository.save(user);
        log.info("User successfully saved to database with ID: {}", user.getUserId());

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(user.getRole())
                .build();

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
}