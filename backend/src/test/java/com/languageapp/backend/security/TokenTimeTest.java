package com.languageapp.backend.security;

import com.languageapp.backend.entity.RefreshToken;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.RefreshTokenRepository;
import com.languageapp.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenTimeTest {
    private static final Instant NOW = Instant.parse("2026-10-25T01:30:00Z");
    @Spy private Clock clock = Clock.fixed(NOW, ZoneId.of("America/New_York"));
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private RefreshTokenService refreshService;

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void refreshToken_ShouldBeInvalidExactlyAtExpiry(long secondsUntilExpiry) {
        RefreshToken token = new RefreshToken();
        token.setUser(new User());
        token.setExpiryDate(NOW.plusSeconds(secondsUntilExpiry));
        if (secondsUntilExpiry > 0) {
            assertSame(token, refreshService.verifyExpiration(token));
        } else {
            assertThrows(BadRequestException.class, () -> refreshService.verifyExpiration(token));
        }
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void jwt_ShouldUseTheSameClockForIssuanceAndParsing() {
        JwtService issuer = new JwtService(clock);
        configureJwt(issuer);
        var user = org.springframework.security.core.userdetails.User
                .withUsername("student@test.com").password("unused").roles("STUDENT").build();
        String token = issuer.generateToken(user);
        assertTrue(issuer.isTokenValid(token, user));

        JwtService later = new JwtService(Clock.fixed(NOW.plusSeconds(60), ZoneId.of("Asia/Tokyo")));
        configureJwt(later);
        assertFalse(later.isTokenValid(token, user));
    }

    private void configureJwt(JwtService service) {
        ReflectionTestUtils.setField(service, "secretKey",
                java.util.Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(service, "jwtExpiration", 60_000L);
    }
}
