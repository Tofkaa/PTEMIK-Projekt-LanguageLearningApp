package com.languageapp.backend.security;

import com.languageapp.backend.entity.PasswordResetToken;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetRevocationTest {
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC);
    @Mock UserRepository users;
    @Mock VerificationTokenRepository verification;
    @Mock PasswordResetTokenRepository resets;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock RefreshTokenService sessions;
    @Mock AuthenticationManager manager;
    @Mock EmailService mail;
    @Mock CourseRepository courses;
    @InjectMocks AuthenticationService service;

    PasswordResetToken token(long seconds) {
        var user = new User(); user.setUserId(UUID.randomUUID()); user.setEmail("reset@example.test"); user.setPasswordHash("old-hash");
        var token = new PasswordResetToken(); token.setUser(user); token.setToken("reset-proof");
        token.setExpiryDate(clock.instant().plusSeconds(seconds)); return token;
    }
    @Test void resetRevokesByUserRatherThanTreatingTheUserIdAsARawToken() {
        var token = token(60); when(resets.findByToken("reset-proof")).thenReturn(Optional.of(token));
        when(encoder.encode("new-password")).thenReturn("new-hash");
        service.resetPassword("reset-proof", "new-password");
        assertEquals("new-hash", token.getUser().getPasswordHash());
        verify(sessions).revokeAllForUser(token.getUser().getUserId());
        verify(sessions, never()).deleteByRawToken(anyString());
        verify(resets).delete(token);
    }
    @Test void anExpiredResetCannotRevokeSessionsOrChangeThePassword() {
        var token = token(0); when(resets.findByToken("reset-proof")).thenReturn(Optional.of(token));
        assertThrows(BadRequestException.class, () -> service.resetPassword("reset-proof", "new-password"));
        assertEquals("old-hash", token.getUser().getPasswordHash());
        verifyNoInteractions(sessions, encoder); verify(users, never()).save(any());
    }
    @Test void anInvalidResetCannotRevokeSessions() {
        assertThrows(BadRequestException.class, () -> service.resetPassword("invalid", "new-password"));
        verifyNoInteractions(sessions, encoder); verify(users, never()).save(any());
    }
}
