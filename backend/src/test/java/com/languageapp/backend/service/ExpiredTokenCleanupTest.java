package com.languageapp.backend.service;

import com.languageapp.backend.scheduler.ExpiredTokenScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.sql.Timestamp;
import java.time.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExpiredTokenCleanupTest {
    @ParameterizedTest @EnumSource(ExpiredTokenCleanup.Kind.class)
    void usesAClockBasedRetentionBoundaryAndBoundedParameterizedClaim(ExpiredTokenCleanup.Kind kind) {
        var jdbc=mock(NamedParameterJdbcTemplate.class);
        Instant now=Instant.parse("2026-10-25T01:30:00Z");
        var cleanup=new ExpiredTokenCleanup(jdbc,Clock.fixed(now,ZoneId.of("America/New_York")));
        when(jdbc.update(anyString(),anyMap())).thenReturn(7);
        assertEquals(7,cleanup.purge(kind));
        var sql=ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked") var params=(ArgumentCaptor<Map<String,Object>>)(ArgumentCaptor<?>)ArgumentCaptor.forClass(Map.class);
        verify(jdbc).update(sql.capture(),params.capture());
        assertEquals(Timestamp.from(now.minus(Duration.ofHours(24))),params.getValue().get("cutoff"));
        assertEquals(100,params.getValue().get("limit"));assertTrue(sql.getValue().contains("for update of u skip locked"));
        assertTrue(sql.getValue().contains("limit :limit"));assertFalse(sql.getValue().contains(now.toString()));
    }

    @Test void oneFailedTableDoesNotSkipOtherKindsOrSpinAnUnboundedLoop() {
        var cleanup=mock(ExpiredTokenCleanup.class);
        when(cleanup.purge(ExpiredTokenCleanup.Kind.REFRESH)).thenThrow(new IllegalStateException("synthetic secret must not be logged"));
        new ExpiredTokenScheduler(cleanup).cleanExpired();
        for(var kind:ExpiredTokenCleanup.Kind.values())verify(cleanup).purge(kind);
        verifyNoMoreInteractions(cleanup);
    }
}
