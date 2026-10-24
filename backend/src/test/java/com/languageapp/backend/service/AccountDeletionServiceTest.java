package com.languageapp.backend.service;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.AuthProvider;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {
    @Mock Clock clock;
    @Mock UserRepository users;
    @Mock AccountDeletionTokenRepository tokens;
    @Mock RefreshTokenService refreshTokens;
    @Mock UserService userService;
    @Mock EmailService emailService;
    @InjectMocks AccountDeletionService service;
    final Instant now = Instant.parse("2026-10-24T13:00:00Z");
    User user;
    RefreshToken session;
    AccountDeletionToken stored;

    @BeforeEach void setup() {
        user = new User(); user.setUserId(UUID.randomUUID()); user.setEmail("oauth@example.test");
        user.setAuthProvider(AuthProvider.GOOGLE); user.setProviderId("provider-123");
        session = new RefreshToken(); session.setUser(user); session.setTokenHash("session-hash"); session.setExpiryDate(now.plusSeconds(3600));
        lenient().when(clock.instant()).thenReturn(now);
        lenient().when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        lenient().when(users.findByEmailForAccountDeletion(user.getEmail())).thenReturn(Optional.of(user));
        lenient().when(refreshTokens.findByRawToken("session")).thenReturn(Optional.of(session));
        lenient().when(tokens.findById(user.getUserId())).thenAnswer(i -> Optional.ofNullable(stored));
        lenient().when(tokens.saveAndFlush(any())).thenAnswer(i -> { stored = i.getArgument(0); return stored; });
    }

    String issue() {
        service.request(user.getEmail(), "session");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAccountDeletionConfirmation(eq(user.getEmail()), code.capture());
        return code.getValue();
    }

    @ParameterizedTest @EnumSource(value = AuthProvider.class, names = {"GOOGLE", "DISCORD"})
    void storesOnlyHashAndEmailsOnlyTheExistingAddress(AuthProvider provider) {
        user.setAuthProvider(provider);
        String secret = issue();
        assertTrue(secret.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(secret, stored.getTokenHash());
        assertEquals(64, stored.getTokenHash().length());
        assertEquals(now.plusSeconds(900), stored.getExpiresAt());
        assertEquals("session-hash", stored.getSessionHash());
        assertEquals(1, stored.getRequestsInWindow());
        verifyNoInteractions(userService);
    }

    @Test void previewIsNonConsumingAndRepeatable() {
        String secret = issue();
        assertEquals(user.getEmail(), service.preview(user.getEmail(), "session", secret).email());
        service.preview(user.getEmail(), "session", secret);
        assertNotNull(stored.getTokenHash());
        verify(tokens, never()).save(any());
        verifyNoInteractions(userService);
    }

    @Test void confirmationRequiresCheckboxThenConsumesOnlyOnce() {
        String secret = issue();
        assertThrows(BadRequestException.class, () -> service.confirm(user.getEmail(), "session", secret, false));
        verifyNoInteractions(userService);
        service.confirm(user.getEmail(), "session", secret, true);
        assertNull(stored.getTokenHash());
        verify(userService).anonymizeAccount(user);
        assertThrows(BadRequestException.class, () -> service.confirm(user.getEmail(), "session", secret, true));
        verify(userService, times(1)).anonymizeAccount(user);
    }

    @Test void exactExpiryAndWrongSecretsAreRejectedWithoutDeleting() {
        String secret = issue();
        assertThrows(BadRequestException.class, () -> service.preview(user.getEmail(), "session", "invalid"));
        String different = (secret.charAt(0) == 'A' ? "B" : "A") + secret.substring(1);
        assertThrows(BadRequestException.class, () -> service.confirm(user.getEmail(), "session", different, true));
        when(clock.instant()).thenReturn(stored.getExpiresAt());
        assertThrows(BadRequestException.class, () -> service.confirm(user.getEmail(), "session", secret, true));
        assertNotNull(stored.getTokenHash());
        verifyNoInteractions(userService);
    }

    @Test void renewedSessionAndChangedAccountInvalidateEarlierApproval() {
        String secret = issue();
        session.setTokenHash("new-login-session");
        assertThrows(BadRequestException.class, () -> service.confirm(user.getEmail(), "session", secret, true));
        session.setTokenHash("session-hash");
        user.setProviderId("another-provider-identity");
        assertThrows(BadRequestException.class, () -> service.confirm(user.getEmail(), "session", secret, true));
        verifyNoInteractions(userService);
    }

    @Test void missingExpiredOrOtherAccountSessionCannotIssueOrConsume() {
        assertThrows(BadRequestException.class, () -> service.request(user.getEmail(), null));
        session.setExpiryDate(now);
        assertThrows(BadRequestException.class, () -> service.request(user.getEmail(), "session"));
        session.setExpiryDate(now.plusSeconds(3600));
        User other = new User(); other.setUserId(UUID.randomUUID()); session.setUser(other);
        assertThrows(BadRequestException.class, () -> service.request(user.getEmail(), "session"));
        verifyNoInteractions(emailService, userService);
        verify(tokens, never()).saveAndFlush(any());
    }

    @Test void reissueRevokesOldLinkAndAppliesCooldownAndHourlyLimit() {
        String first = issue();
        assertThrows(BadRequestException.class, () -> service.request(user.getEmail(), "session"));
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        service.request(user.getEmail(), "session");
        assertThrows(BadRequestException.class, () -> service.preview(user.getEmail(), "session", first));
        for (int n = 2; n < 5; n++) {
            when(clock.instant()).thenReturn(now.plusSeconds(n * 60L));
            service.request(user.getEmail(), "session");
        }
        when(clock.instant()).thenReturn(now.plusSeconds(300));
        assertThrows(BadRequestException.class, () -> service.request(user.getEmail(), "session"));
        verify(emailService, times(5)).sendAccountDeletionConfirmation(eq(user.getEmail()), anyString());
        when(clock.instant()).thenReturn(now.plusSeconds(3600));
        session.setExpiryDate(now.plusSeconds(7200));
        service.request(user.getEmail(), "session");
        assertEquals(1, stored.getRequestsInWindow());
    }

    @Test void localAndDisabledAccountsCannotUseEmailDeletion() {
        user.setAuthProvider(AuthProvider.LOCAL);
        assertThrows(BadRequestException.class, () -> service.request(user.getEmail(), "session"));
        user.setAuthProvider(AuthProvider.DISCORD); user.setActive(false);
        assertThrows(BadRequestException.class, () -> service.request(user.getEmail(), "session"));
        verifyNoInteractions(emailService, userService);
    }
}
