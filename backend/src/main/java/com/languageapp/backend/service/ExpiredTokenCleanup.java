package com.languageapp.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/** Best-effort housekeeping is independent of invalid-token request rollbacks. PostgreSQL only. */
@Service
@RequiredArgsConstructor
public class ExpiredTokenCleanup {
    public static final int BATCH_SIZE = 100;
    public static final Duration EXPIRED_RETENTION = Duration.ofHours(24);
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** Table/column identifiers are constants, never request data. */
    public enum Kind {
        REFRESH("refresh_tokens", "refresh_token_id", "expiry_date", "and t.created_at < :cutoff"),
        PASSWORD_RESET("password_reset_tokens", "token_id", "expiry_date", "and t.created_at < :cutoff"),
        VERIFICATION("verification_tokens", "token_id", "expiry_date", "and t.created_at < :cutoff"),
        EMAIL_CHANGE("email_change_tokens", "id", "expiry_date", ""),
        ACCOUNT_DELETION("account_deletion_tokens", "user_id", "expires_at",
                "and t.last_requested_at < :cutoff and t.rate_window_start < :cutoff"),
        OAUTH_LINK("oauth_link_intents", "user_id", "expires_at",
                "and (t.last_requested_at is null or t.last_requested_at < :cutoff) "
                        + "and (t.rate_window_start is null or t.rate_window_start < :cutoff)");

        private final String sql;
        Kind(String table, String id, String expiry, String retainedMetadata) {
            String eligible = "t." + expiry + " < :cutoff " + retainedMetadata;
            // Claims follow the same order as issuance: User first, then its token row.
            // A bounded candidate snapshot prevents a scan over all users or live credentials.
            sql = "with candidates as materialized (select t." + id + " as candidate_id, t.user_id as owner_id from "
                    + table + " t where " + eligible + " order by t." + expiry + ", t." + id + " limit :limit), "
                    + "owners as materialized (select u.user_id from users u where u.user_id in "
                    + "(select owner_id from candidates) order by u.user_id for update of u skip locked), "
                    + "expired as (select t." + id + " from " + table + " t "
                    + "join candidates c on c.candidate_id = t." + id + " join owners o on o.user_id = c.owner_id "
                    + "where " + eligible + " for update of t skip locked) "
                    + "delete from " + table + " t using expired e where t." + id + " = e." + id
                    + " and t." + expiry + " < :cutoff";
        }
    }

    /** One short transaction/table; accounts/tokens locked by issuance are skipped, and renewed challenges are rechecked. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public int purge(Kind kind) {
        var cutoff = Timestamp.from(clock.instant().minus(EXPIRED_RETENTION));
        return jdbc.update(kind.sql, Map.of("cutoff", cutoff, "limit", BATCH_SIZE));
    }
}
