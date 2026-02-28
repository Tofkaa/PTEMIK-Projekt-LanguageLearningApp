package com.languageapp.backend.security;

import com.languageapp.backend.entity.RefreshToken;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.RefreshTokenRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for managing the lifecycle of refresh tokens.
 * <p>
 * Handles secure token generation, cryptographic hashing (SHA-256),
 * validation, and automatic invalidation of expired sessions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.refresh.expiration}")
    private Long refreshTokenDurationMs;

    /**
     * Creates a cryptographically secure refresh token for the specified user.
     * Deletes any existing tokens for the user to maintain a single active session.
     *
     * @param userId the UUID of the user
     * @return the raw, unhashed refresh token (to be sent via HTTP-only cookie)
     * @throws ResourceNotFoundException if the user does not exist in the database
     */
    @Transactional
    public String createRefreshToken(UUID userId) {
        log.debug("Creating new refresh token for user ID: {}", userId);

        refreshTokenRepository.deleteByUserUserId(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Failed to create refresh token: User not found with ID {}", userId);
                    return new ResourceNotFoundException("No user found for token generation.");
                });

        String rawToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(LocalDateTime.now().plus(refreshTokenDurationMs, ChronoUnit.MILLIS));

        // Csak a hash-t mentjük!
        refreshToken.setTokenHash(hashToken(rawToken));

        refreshTokenRepository.save(refreshToken);

        log.info("Successfully created and stored refresh token hash for user ID: {}", userId);
        return rawToken;
    }

    /**
     * Verifies if the provided refresh token is still valid (not expired).
     * If expired, it automatically deletes the token from the database.
     *
     * @param token the {@link RefreshToken} entity to check
     * @return the valid {@link RefreshToken}
     * @throws BadRequestException if the token has expired
     */
    @Transactional
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            log.warn("Refresh token expired for user ID: {}. Deleting from database.", token.getUser().getUserId());
            refreshTokenRepository.delete(token);

            throw new BadRequestException("Session expired, please log in again.");
        }
        return token;
    }

    /**
     * Finds a refresh token in the database by its raw string value.
     * The raw token is hashed before querying the database to ensure security.
     *
     * @param rawToken the raw token string provided by the client
     * @return an {@link Optional} containing the token entity if found
     */
    public Optional<RefreshToken> findByRawToken(String rawToken) {
        log.debug("Searching for refresh token by hash in database.");
        return refreshTokenRepository.findByTokenHash(hashToken(rawToken));
    }

    /**
     * Deletes the refresh token from the DataBase.
     * @param rawToken the token to be deleted
     */
    @Transactional
    public void deleteByRawToken(String rawToken) {
        log.info("Deleting refresh token for logout...");
        findByRawToken(rawToken).ifPresent(refreshTokenRepository::delete);
    }

    /**
     * Generates a one-way SHA-256 hash of the provided token string.
     *
     * @param token the raw token string
     * @return the Base64 encoded SHA-256 hash
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Critical Security Error: SHA-256 algorithm not found in the JVM!", e);
            throw new IllegalStateException("Critical server error while securing the token.", e);
        }
    }
}