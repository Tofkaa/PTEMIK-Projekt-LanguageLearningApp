package com.languageapp.backend.observability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

/** Bounded read-only aggregate over this database/role's connections; never returns query text or actors. */
@Component
public class DatabaseWaitProbe {
    public record Snapshot(int waiters,double oldestSeconds,int activeQueries,double oldestQuerySeconds) {
        public Snapshot(int waiters,double oldestSeconds){this(waiters,oldestSeconds,0,0);}
    }
    private final JdbcTemplate jdbc;
    public DatabaseWaitProbe(DataSource source){jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(2);}
    public Snapshot sample(){return jdbc.queryForObject("""
            select count(*) filter (where wait_event_type='Lock') as waiters,
              coalesce(max(extract(epoch from clock_timestamp()-query_start)) filter (where wait_event_type='Lock'),0) as oldest_seconds,
              count(*) filter (where state='active') as active_queries,
              coalesce(max(extract(epoch from clock_timestamp()-query_start)) filter (where state='active'),0) as oldest_query_seconds
            from pg_stat_activity where datname=current_database() and usename=current_user and pid<>pg_backend_pid()
            """,(row,index)->new Snapshot(row.getInt("waiters"),Math.max(0,row.getDouble("oldest_seconds")),row.getInt("active_queries"),Math.max(0,row.getDouble("oldest_query_seconds"))));}
}
