package com.languageapp.backend.security;

import com.languageapp.backend.dto.request.LoginRequest;
import com.languageapp.backend.dto.request.RegisterRequest;
import com.languageapp.backend.dto.response.AuthResponse;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.entity.VerificationToken;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.repository.VerificationTokenRepository;
import com.languageapp.backend.service.EmailService;
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
import java.util.UUID;

/**
 * Service responsible for user authentication and registration logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    public record AuthResult(AuthResponse responseDto, String refreshToken) {}

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
        user.setVerified(false);

        if (request.getPreferredDifficulty() != null && !request.getPreferredDifficulty().isBlank()) {
            try {
                user.setPreferredDifficulty(com.languageapp.backend.enums.DifficultyLevel.valueOf(request.getPreferredDifficulty().toUpperCase()));
            } catch (IllegalArgumentException e) {
                user.setPreferredDifficulty(com.languageapp.backend.enums.DifficultyLevel.DYNAMIC);
            }
        } else {
            user.setPreferredDifficulty(com.languageapp.backend.enums.DifficultyLevel.DYNAMIC);
        }

        if (Role.ADMIN.equals(request.getRole())) {
            log.warn("Security Alert: Attempted ADMIN registration with email: {}", request.getEmail());
            throw new BadRequestException("Cannot register as an ADMIN user.");
        }

        user.setRole(request.getRole() != null ? request.getRole() : Role.STUDENT);
        log.info("Registering {} user with email: {}...", user.getRole(), request.getEmail());

        String generatedFriendCode;
        do {
            generatedFriendCode = generateFriendCode();
        } while (userRepository.existsByFriendCode(generatedFriendCode));
        user.setFriendCode(generatedFriendCode);

        String generatedTag;
        int attempts = 0;
        do {
            int randomTag = 1000 + new java.util.Random().nextInt(9000);
            generatedTag = String.valueOf(randomTag);
            attempts++;
            if (attempts > 100) {
                throw new BadRequestException("Túl sok felhasználó van ezzel a névvel. Kérlek, válassz egy egyedibb nevet!");
            }
        } while (userRepository.existsByNameAndUserTag(request.getName(), generatedTag));
        user.setUserTag(generatedTag);

        userRepository.save(user);
        log.info("User successfully saved to database with ID: {}", user.getUserId());

        String tokenStr = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(tokenStr);
        verificationToken.setUser(user);
        verificationToken.setExpiryDate(LocalDateTime.now().plusHours(24));
        verificationTokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user.getEmail(), user.getName(), tokenStr);


        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
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
                user.getRole(),
                user.getUserTag(),
                user.getFriendCode(),
                user.isVerified()
        );

        log.info("Tokens successfully generated for user: {}", user.getEmail());
        return new AuthResult(response, refreshToken);
    }

    /**
     * Verifies the user's email using the provided token.
     */
    @Transactional
    public void verifyEmail(String token) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Érvénytelen vagy lejárt token!"));

        if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            verificationTokenRepository.delete(verificationToken);
            throw new BadRequestException("A megerősítő link lejárt. Kérlek, igényelj újat a profilodban.");
        }

        User user = verificationToken.getUser();
        user.setVerified(true);
        userRepository.save(user);

        verificationTokenRepository.delete(verificationToken);
        log.info("User {} successfully verified their email address.", user.getEmail());
    }

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
                user.getRole(),
                user.getUserTag(),
                user.getFriendCode(),
                user.isVerified()
        );

        log.info("User successfully authenticated: {}", user.getEmail());
        return new AuthResult(response, refreshToken);
    }

    @Transactional
    public String logout(String rawRefreshToken) {
        return refreshTokenService.deleteByRawToken(rawRefreshToken);
    }

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
                            .authorities("ROLE_" + user.getRole().name())
                            .build();

                    String newAccessToken = jwtService.generateToken(userDetails);
                    log.info("Access token successfully refreshed for user: {}", user.getEmail());

                    return new AuthResponse(
                            newAccessToken,
                            user.getUserId(),
                            user.getName(),
                            user.getEmail(),
                            user.getRole(),
                            user.getUserTag(),
                            user.getFriendCode(),
                            user.isVerified()
                    );
                })
                .orElseThrow(() -> {
                    log.warn("Refresh token validation failed: Token not found or invalid hash.");
                    return new BadRequestException("An error occurred, please login again.");
                });
    }

    private String generateFriendCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.util.Random rnd = new java.util.Random();
        StringBuilder sb = new StringBuilder(7);
        for (int i = 0; i < 6; i++) {
            if (i == 3) {
                sb.append('-');
            } else {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
        }
        return sb.toString();
    }
}