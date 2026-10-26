package com.languageapp.backend.service;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class InvitationRequestLimiterTest {
    @Test void boundedWindowsArePerAccountAndActionAndResetAfterOneMinute() {
        Clock clock = mock(Clock.class); Instant now = Instant.parse("2026-10-26T08:00:00Z");
        when(clock.instant()).thenReturn(now); var limiter = new InvitationRequestLimiter(clock);
        for (int i=0;i<10;i++) limiter.check("owner", "manage");
        assertThrows(InvitationRequestLimiter.LimitExceeded.class, () -> limiter.check("owner", "manage"));
        assertDoesNotThrow(() -> limiter.check("other", "manage"));
        assertDoesNotThrow(() -> limiter.check("owner", "preview"));
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertDoesNotThrow(() -> limiter.check("owner", "manage"));
    }
}
