package com.languageapp.backend.service;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.AuthProvider;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.AccountDeletionTokenRepository;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {
    private final Clock clock;
    private final UserRepository users;
    private final AccountDeletionTokenRepository tokens;
    private final RefreshTokenService refreshTokens;
    private final UserService userService;
    private final EmailService emailService;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID = "A törlési jóváhagyás érvénytelen, lejárt vagy már felhasznált. Kérj új linket a Beállításokban.";

    public record Preview(String email, Instant expiresAt) {}

    @Transactional
    public Preview request(String email, String rawSession) {
        // Serializes issue/reissue/consume, including the initially absent challenge row.
        User user = account(email, true);
        RefreshToken session = session(user, rawSession);
        Instant now = clock.instant();
        AccountDeletionToken token = tokens.findById(user.getUserId()).orElseGet(AccountDeletionToken::new);
        if (token.getLastRequestedAt() != null && now.isBefore(token.getLastRequestedAt().plusSeconds(60))) {
            throw new BadRequestException("Új törlési linket egy perc elteltével kérhetsz.");
        }
        if (token.getRateWindowStart() == null || !now.isBefore(token.getRateWindowStart().plus(1, ChronoUnit.HOURS))) {
            token.setRateWindowStart(now);
            token.setRequestsInWindow(0);
        }
        if (token.getRequestsInWindow() >= 5) {
            throw new BadRequestException("Egy órán belül legfeljebb öt törlési linket kérhetsz. Próbáld újra később.");
        }
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        token.setUserId(user.getUserId());
        token.setTokenHash(hash(rawToken));
        token.setSessionHash(session.getTokenHash());
        token.setAccountFingerprint(fingerprint(user));
        token.setExpiresAt(now.plus(15, ChronoUnit.MINUTES));
        token.setLastRequestedAt(now);
        token.setRequestsInWindow(token.getRequestsInWindow() + 1);
        tokens.saveAndFlush(token);
        String recipient = user.getEmail();
        Runnable send = () -> emailService.sendAccountDeletionConfirmation(recipient, rawToken);
        // Never email a challenge rolled back by a failed transaction.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
        return new Preview(recipient, token.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public Preview preview(String email, String rawSession, String rawToken) {
        User user = account(email, false);
        AccountDeletionToken token = validate(user, session(user, rawSession), rawToken);
        return new Preview(user.getEmail(), token.getExpiresAt());
    }

    @Transactional
    public void confirm(String email, String rawSession, String rawToken, boolean confirmed) {
        if (!confirmed) throw new BadRequestException("A fióktörléshez jelöld be a külön megerősítő négyzetet.");
        User user = account(email, true);
        AccountDeletionToken token = validate(user, session(user, rawSession), rawToken);
        token.setTokenHash(null);
        tokens.delete(token); // Removed atomically with anonymization; the account can no longer request links.
        userService.anonymizeAccount(user);
    }

    private AccountDeletionToken validate(User user, RefreshToken session, String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw new BadRequestException(INVALID);
        AccountDeletionToken token = tokens.findById(user.getUserId()).orElseThrow(() -> new BadRequestException(INVALID));
        if (token.getTokenHash() == null || !MessageDigest.isEqual(token.getTokenHash().getBytes(StandardCharsets.UTF_8),
                hash(rawToken).getBytes(StandardCharsets.UTF_8)) || !clock.instant().isBefore(token.getExpiresAt())
                || !Objects.equals(token.getSessionHash(), session.getTokenHash())
                || !Objects.equals(token.getAccountFingerprint(), fingerprint(user))) {
            throw new BadRequestException(INVALID);
        }
        return token;
    }

    private User account(String email, boolean lock) {
        User user = (lock ? users.findByEmailForAccountDeletion(email) : users.findByEmail(email))
                .orElseThrow(() -> new BadRequestException(INVALID));
        if (!user.isActive() || (user.getAuthProvider() != AuthProvider.GOOGLE && user.getAuthProvider() != AuthProvider.DISCORD)) {
            throw new BadRequestException("Ez a jóváhagyási út csak aktív Google- és Discord-fióknál használható.");
        }
        return user;
    }

    private RefreshToken session(User user, String rawSession) {
        if (rawSession == null || rawSession.isBlank()) throw sessionError();
        RefreshToken session = refreshTokens.findByRawToken(rawSession).orElseThrow(this::sessionError);
        if (!user.getUserId().equals(session.getUser().getUserId()) || !clock.instant().isBefore(session.getExpiryDate())) {
            throw sessionError();
        }
        return session;
    }

    private BadRequestException sessionError() {
        return new BadRequestException("A törlési linket a kérést indító bejelentkezésből, ugyanabban a böngészőprofilban nyisd meg. Új belépés után új linket kell kérned.");
    }

    private String fingerprint(User user) {
        return hash(user.getEmail() + "\n" + user.getAuthProvider() + "\n" + user.getProviderId() + "\n" + user.getPasswordHash());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
