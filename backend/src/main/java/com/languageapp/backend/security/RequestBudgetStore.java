package com.languageapp.backend.security;

import com.languageapp.backend.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Shared PostgreSQL GCRA buckets: atomic admission, gradual refill, and bounded burst. */
@Service @RequiredArgsConstructor
public class RequestBudgetStore {
    private final JdbcTemplate jdbc;
    public record Budget(String key, int capacity, int seconds) {
        public Budget {
            if (key == null || key.isBlank() || capacity < 1 || capacity > 1_000_000 || seconds < 1 || seconds > 86400)
                throw new IllegalArgumentException("Invalid request budget");
        }
    }
    public static Budget budget(String action, String scope, String identity, int capacity, int seconds) {
        try {
            byte[] bytes = (action + "\0" + scope + "\0" + identity).getBytes(StandardCharsets.UTF_8);
            return new Budget(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), capacity, seconds);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private long now() {
        // One authoritative time source across replicas; UTC microseconds match PostgreSQL precision.
        return Objects.requireNonNull(jdbc.queryForObject("SELECT floor(extract(epoch FROM clock_timestamp())*1000000)::bigint", Long.class));
    }
    // Admission commits before the business transaction; a wrong password/rollback must consume its budget.
    @Transactional(propagation=Propagation.REQUIRES_NEW, noRollbackFor=TooManyRequestsException.class, timeout=3)
    public void check(List<Budget> budgets) {
        long now = now();
        for (Budget budget : budgets) {
            long spacing = (budget.seconds()*1_000_000L + budget.capacity()-1)/budget.capacity();
            long tolerance = Math.multiplyExact(budget.capacity()-1L, spacing);
            List<Long> admitted = jdbc.query("""
                INSERT INTO request_budgets(bucket_key,next_at) VALUES (?,?)
                ON CONFLICT(bucket_key) DO UPDATE SET next_at=GREATEST(request_budgets.next_at,?)+?
                WHERE request_budgets.next_at<=? RETURNING next_at
                """, (rs,index)->rs.getLong(1), budget.key(), now+spacing, now, spacing, now+tolerance);
            if (admitted.isEmpty()) {
                Long next = jdbc.queryForObject("SELECT next_at FROM request_budgets WHERE bucket_key=?", Long.class, budget.key());
                long delay = next == null ? 1 : Math.max(1, (next-now-tolerance+999_999L)/1_000_000L);
                throw new TooManyRequestsException(delay);
            }
        }
    }
    /** Once the bucket has fully refilled, removing it is equivalent to an empty bucket. */
    @Transactional(propagation=Propagation.REQUIRES_NEW, timeout=3)
    public int prune() {
        long now=now();
        return jdbc.update("""
            WITH expired AS (SELECT bucket_key FROM request_budgets WHERE next_at<=?
                ORDER BY next_at,bucket_key LIMIT 50000 FOR UPDATE SKIP LOCKED)
            DELETE FROM request_budgets b USING expired e WHERE b.bucket_key=e.bucket_key AND b.next_at<=?
            """, now, now);
    }
}
