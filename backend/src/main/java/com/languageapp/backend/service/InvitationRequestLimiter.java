package com.languageapp.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Per-instance limits for authenticated invitation operations; domain authorization is separate. */
@Component @RequiredArgsConstructor
public class InvitationRequestLimiter {
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private record Window(Instant started, int count) { }
    public static class LimitExceeded extends RuntimeException { }
    public void check(String subject, String action) {
        Instant now = clock.instant();
        if (windows.size() > 1000) windows.entrySet().removeIf(e -> !now.isBefore(e.getValue().started.plusSeconds(60)));
        String key = subject+":"+action;
        int limit = "preview".equals(action) ? 60 : "confirm".equals(action) ? 20 : 10;
        if (!windows.containsKey(key) && windows.size() >= 10000) throw new LimitExceeded();
        windows.compute(key, (ignored, window) -> {
            if (window == null || !now.isBefore(window.started.plusSeconds(60)) || now.isBefore(window.started)) return new Window(now, 1);
            if (window.count >= limit) throw new LimitExceeded();
            return new Window(window.started, window.count+1);
        });
    }
}
