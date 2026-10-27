package com.languageapp.backend.security;

import com.languageapp.backend.entity.User;
import com.languageapp.backend.entity.VerificationToken;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationConsumptionTest {
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);
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

    User account() {
        User user = new User(); user.setUserId(UUID.randomUUID()); user.setEmail("verification@example.test");
        when(verification.findUserIdByToken("proof")).thenReturn(Optional.of(user.getUserId()));
        when(users.findForSessionUpdate(user.getUserId())).thenReturn(Optional.of(user));
        return user;
    }
    @ParameterizedTest @ValueSource(longs = {-1, 0, 1})
    void checksTheExclusiveExpiryBoundaryOnlyAfterLockingTheAccount(long seconds) {
        User user = account();
        VerificationToken token = new VerificationToken(); token.setUser(user); token.setToken("proof");
        token.setExpiryDate(clock.instant().plusSeconds(seconds));
        when(verification.findByToken("proof")).thenReturn(Optional.of(token));
        if (seconds > 0) {
            service.verifyEmail("proof");
            assertTrue(user.isVerified()); verify(verification).delete(token);
        } else {
            assertThrows(BadRequestException.class, () -> service.verifyEmail("proof"));
            assertFalse(user.isVerified()); verify(users, never()).save(any()); verify(verification, never()).delete(any());
        }
        InOrder order = inOrder(verification, users);
        order.verify(verification).findUserIdByToken("proof");
        order.verify(users).findForSessionUpdate(user.getUserId());
        order.verify(verification).findByToken("proof");
    }
    @Test void aTokenConsumedWhileWaitingCannotVerifyOrSaveTheAccountAgain() {
        User user = account();
        assertThrows(BadRequestException.class, () -> service.verifyEmail("proof"));
        assertFalse(user.isVerified()); verify(users, never()).save(any());
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void disabledOrDeletedAccountsCannotBeVerified(boolean deleted) {
        User user = account();
        if (deleted) user.setRole(Role.DELETED); else user.setActive(false);
        assertThrows(BadRequestException.class, () -> service.verifyEmail("proof"));
        verify(verification, never()).findByToken(anyString()); verify(users, never()).save(any());
    }
    @Test void invalidTokensDoNotLockAnUnrelatedAccount() {
        assertThrows(BadRequestException.class, () -> service.verifyEmail("missing"));
        verifyNoInteractions(users); verify(verification, never()).findByToken(anyString());
    }
}
