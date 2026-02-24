package com.languageapp.backend.security;

import com.languageapp.backend.entity.RefreshToken;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.repository.RefreshTokenRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.refresh.expiration}")
    private Long refreshTokenDurationMs;

    @Transactional
    public String createRefreshToken(UUID userId) {
        refreshTokenRepository.deleteByUserUserId(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String rawToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(LocalDateTime.now().plus(refreshTokenDurationMs, ChronoUnit.MILLIS));

        refreshToken.setTokenHash(hashToken(rawToken));

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("A Refresh Token lejárt! Kérlek, jelentkezz be újra.");
        }
        return token;
    }

    public Optional<RefreshToken> findByRawToken(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hashToken(rawToken));
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Nem sikerült titkosítani a tokent", e);
        }
    }
}