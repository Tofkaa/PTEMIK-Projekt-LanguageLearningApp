package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.service.*;
import com.languageapp.backend.service.ExpiredTokenCleanup.Kind;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="TOKEN_CLEANUP_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_token_cleanup_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${TOKEN_CLEANUP_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create","spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class ExpiredTokenCleanupPersistenceTest {
    static final Instant NOW=Instant.parse("2026-10-28T10:00:00Z");
    static final Instant OLD=NOW.minus(Duration.ofDays(3));
    static final Instant CUTOFF=NOW.minus(Duration.ofHours(24));
    @Autowired ExpiredTokenCleanup cleanup;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired UserRepository users;
    @MockitoBean Clock clock;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;
    record Row(Kind kind,UUID id,UUID userId) { }
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        tx=new TransactionTemplate(manager);
        for(var kind:Kind.values())jdbc.update("delete from "+table(kind));
    }
    User user() {
        return tx.execute(s->{
            String key=UUID.randomUUID().toString().substring(0,8);
            var c=new Course();c.setLanguageCode(key);c.setLanguageName("Cleanup");em.persist(c);
            var u=new User();u.setName("Cleanup "+key);u.setEmail(key+"@example.test");u.setActiveCourse(c);u.setVerified(true);u.setXp(57);em.persist(u);em.flush();return u;
        });
    }
    String table(Kind kind) { return switch(kind) {
        case REFRESH->"refresh_tokens";case PASSWORD_RESET->"password_reset_tokens";case VERIFICATION->"verification_tokens";
        case EMAIL_CHANGE->"email_change_tokens";case ACCOUNT_DELETION->"account_deletion_tokens";case OAUTH_LINK->"oauth_link_intents";}; }
    String idColumn(Kind kind) { return switch(kind) {
        case REFRESH->"refresh_token_id";case PASSWORD_RESET,VERIFICATION->"token_id";case EMAIL_CHANGE->"id";case ACCOUNT_DELETION,OAUTH_LINK->"user_id";}; }
    Row insert(Kind kind,Instant expires,Instant metadata) { return insert(kind,expires,metadata,user()); }
    Row insert(Kind kind,Instant expires,Instant metadata,User user) {
        UUID id=UUID.randomUUID();UUID uid=user.getUserId();String secret=UUID.randomUUID().toString();
        var exp=Timestamp.from(expires);var meta=Timestamp.from(metadata);
        switch(kind) {
            case REFRESH->jdbc.update("insert into refresh_tokens(refresh_token_id,user_id,token_hash,expiry_date,created_at) values(?,?,?,?,?)",id,uid,secret,exp,meta);
            case PASSWORD_RESET,VERIFICATION->jdbc.update("insert into "+table(kind)+"(token_id,user_id,token,expiry_date,created_at) values(?,?,?,?,?)",id,uid,secret,exp,meta);
            case EMAIL_CHANGE->jdbc.update("insert into email_change_tokens(id,user_id,otp_code,new_email,expiry_date,failed_attempts) values(?,?,?,?,?,?)",id,uid,"123456","new@example.test",exp,3);
            case ACCOUNT_DELETION->{id=uid;jdbc.update("insert into account_deletion_tokens(user_id,token_hash,session_hash,account_fingerprint,expires_at,last_requested_at,rate_window_start,requests_in_window) values(?,?,?,?,?,?,?,?)",id,"0".repeat(64),"session","1".repeat(64),exp,meta,meta,5);}
            case OAUTH_LINK->{id=uid;jdbc.update("insert into oauth_link_intents(user_id,request_id,provider,session_id,session_hash,fingerprint,failed_attempts,expires_at,last_requested_at,rate_window_start,requests_in_window) values(?,?,?,?,?,?,?,?,?,?,?)",id,UUID.randomUUID(),"GOOGLE",UUID.randomUUID(),"0".repeat(64),"1".repeat(64),3,exp,meta,meta,5);}
        }
        return new Row(kind,id,uid);
    }
    boolean exists(Row row) {return jdbc.queryForObject("select count(*) from "+table(row.kind)+" where "+idColumn(row.kind)+"=?",Integer.class,row.id)==1;}

    @ParameterizedTest @EnumSource(Kind.class)
    void deletesOnlyAgedExpiredRecordsAndPreservesBoundaryLiveAndRecentState(Kind kind) {
        Row old=insert(kind,CUTOFF.minusSeconds(1),OLD),boundary=insert(kind,CUTOFF,OLD);
        Row recent=insert(kind,NOW.minusSeconds(60),OLD),live=insert(kind,NOW.plusSeconds(60),OLD);
        Row freshState=kind==Kind.EMAIL_CHANGE?null:insert(kind,OLD,NOW.minusSeconds(60));
        assertEquals(1,cleanup.purge(kind));assertFalse(exists(old));
        for(Row row:List.of(boundary,recent,live))assertTrue(exists(row));
        if(freshState!=null)assertTrue(exists(freshState));
        assertEquals(57,users.findById(old.userId).orElseThrow().getXp());verifyNoInteractions(mail,sse,images);
    }

    @Test void batchLimitAndRepeatedTicksDrainBacklogWithoutDeletingLiveSessions() {
        User u=user();for(int i=0;i<250;i++)insert(Kind.REFRESH,OLD,OLD,u);
        Row live=insert(Kind.REFRESH,NOW.plusSeconds(100),OLD,u);
        assertEquals(100,cleanup.purge(Kind.REFRESH));assertEquals(100,cleanup.purge(Kind.REFRESH));
        assertEquals(50,cleanup.purge(Kind.REFRESH));assertEquals(0,cleanup.purge(Kind.REFRESH));assertTrue(exists(live));
    }

    @Test void lockedChallengeIsSkippedAndCommittedReissueSurvives() throws Exception {
        Row row=insert(Kind.ACCOUNT_DELETION,OLD,OLD);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var renewal=pool.submit(()->tx.execute(s->{
                users.findForSessionUpdate(row.userId).orElseThrow();
                jdbc.update("update account_deletion_tokens set expires_at=?,last_requested_at=?,rate_window_start=? where user_id=?",Timestamp.from(NOW.plusSeconds(900)),Timestamp.from(NOW),Timestamp.from(NOW),row.id);
                locked.countDown();await(release);return null;
            }));
            assertTrue(locked.await(5,TimeUnit.SECONDS));
            assertEquals(0,pool.submit(()->cleanup.purge(Kind.ACCOUNT_DELETION)).get(3,TimeUnit.SECONDS));
            release.countDown();renewal.get(5,TimeUnit.SECONDS);assertTrue(exists(row));assertEquals(0,cleanup.purge(Kind.ACCOUNT_DELETION));
        } finally {release.countDown();pool.shutdownNow();}
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void inFlightUserOperationOwnsItsExpiredTokenEvenBeforeTouchingTheTokenRow(Kind kind) throws Exception {
        Row row=insert(kind,OLD,OLD);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var holder=pool.submit(()->tx.execute(s->{
                users.findForSessionUpdate(row.userId).orElseThrow();locked.countDown();await(release);return null;
            }));
            assertTrue(locked.await(5,TimeUnit.SECONDS));
            assertEquals(0,pool.submit(()->cleanup.purge(kind)).get(3,TimeUnit.SECONDS));
            assertTrue(exists(row));release.countDown();holder.get(5,TimeUnit.SECONDS);
            assertEquals(1,cleanup.purge(kind));assertFalse(exists(row));
        } finally {release.countDown();pool.shutdownNow();}
    }

    @Test void lockedExpiredRowIsEligibleOnTheNextTickAfterRollback() throws Exception {
        Row row=insert(Kind.EMAIL_CHANGE,OLD,OLD);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var holder=pool.submit(()->tx.execute(s->{
                jdbc.queryForList("select id from email_change_tokens where id=? for update",row.id);locked.countDown();await(release);s.setRollbackOnly();return null;
            }));
            assertTrue(locked.await(5,TimeUnit.SECONDS));assertEquals(0,pool.submit(()->cleanup.purge(Kind.EMAIL_CHANGE)).get(3,TimeUnit.SECONDS));
            release.countDown();holder.get(5,TimeUnit.SECONDS);assertEquals(1,cleanup.purge(Kind.EMAIL_CHANGE));assertFalse(exists(row));
        } finally {release.countDown();pool.shutdownNow();}
    }

    @Test void twoReplicaStyleWorkersDeleteEachRecordOnce() throws Exception {
        User u=user();for(int i=0;i<150;i++)insert(Kind.REFRESH,OLD,OLD,u);
        var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        try {
            Callable<Integer> job=()->{await(go);return cleanup.purge(Kind.REFRESH);};
            var a=pool.submit(job);var b=pool.submit(job);go.countDown();
            int removed=a.get(5,TimeUnit.SECONDS)+b.get(5,TimeUnit.SECONDS);
            // Both bounded snapshots can target the same owner; the losing worker skips it rather than waits.
            assertTrue(removed>=100 && removed<=150);
            assertEquals(150-removed,jdbc.queryForObject("select count(*) from refresh_tokens",Integer.class));
            assertEquals(150-removed,cleanup.purge(Kind.REFRESH));assertEquals(0,cleanup.purge(Kind.REFRESH));
        } finally {go.countDown();pool.shutdownNow();}
    }

    @Test void cleanupCommitIsIndependentOfAFailingCallerTransaction() {
        Row row=insert(Kind.PASSWORD_RESET,OLD,OLD);
        tx.executeWithoutResult(s->{assertEquals(1,cleanup.purge(Kind.PASSWORD_RESET));s.setRollbackOnly();});assertFalse(exists(row));
    }

    @Test void allExpiryLeadingIndexesExistAndManyLiveSessionsUseTheRefreshIndex() {
        for(String index:List.of("idx_refresh_expiry_cleanup","idx_reset_expiry_cleanup","idx_verification_expiry_cleanup","idx_email_change_expiry_cleanup","idx_deletion_expiry_cleanup","idx_oauth_link_expiry_cleanup"))
            assertEquals(1,jdbc.queryForObject("select count(*) from pg_indexes where schemaname='public' and indexname=?",Integer.class,index));
        User u=user();
        jdbc.update("insert into refresh_tokens(refresh_token_id,user_id,token_hash,expiry_date,created_at) select gen_random_uuid(),?,gen_random_uuid()::text,?,? from generate_series(1,5000)",u.getUserId(),Timestamp.from(NOW.plusSeconds(3600)),Timestamp.from(OLD));
        insert(Kind.REFRESH,OLD,OLD,u);jdbc.execute("analyze refresh_tokens");
        var plan=jdbc.queryForList("explain select refresh_token_id from refresh_tokens where expiry_date < ? and created_at < ? order by expiry_date,refresh_token_id limit 100 for update skip locked",String.class,Timestamp.from(CUTOFF),Timestamp.from(CUTOFF));
        assertTrue(String.join("\n",plan).contains("idx_refresh_expiry_cleanup"),plan.toString());
        assertEquals(1,cleanup.purge(Kind.REFRESH));assertEquals(5000,jdbc.queryForObject("select count(*) from refresh_tokens",Integer.class));
    }

    void await(CountDownLatch latch) {try {if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("Timed out waiting for fixture checkpoint");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
