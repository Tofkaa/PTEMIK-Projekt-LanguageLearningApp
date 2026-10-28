package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.TooManyRequestsException;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.security.*;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="RATE_LIMIT_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_rate_limit_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${RATE_LIMIT_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create","spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class RateLimitPersistenceTest {
    @Autowired RequestBudgetStore store;
    @Autowired ApiRequestLimiter limiter;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    @Autowired WebApplicationContext context;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    @MockitoBean RequestBudgetCleanup cleanup;
    RequestBudgetStore.Budget budget(int count){return RequestBudgetStore.budget("test","account",UUID.randomUUID().toString(),count,3600);}
    long stored(RequestBudgetStore.Budget b){return jdbc.queryForObject("SELECT next_at FROM request_budgets WHERE bucket_key=?",Long.class,b.key());}
    MockMvc mvc(){return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @Test void concurrentReplicasAndRecreatedLimiterShareOneAdmissionBudget() throws Exception {
        var b=budget(8);var replica=new RequestBudgetStore(jdbc);var pool=Executors.newFixedThreadPool(8);
        var ready=new CountDownLatch(8);var go=new CountDownLatch(1);var admitted=new AtomicInteger();var rejected=new AtomicInteger();
        try {
            var futures=new ArrayList<Future<?>>();
            for(int i=0;i<24;i++){final int n=i;futures.add(pool.submit(()->{
                ready.countDown();try{if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("start timeout");}
                catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
                try{(n%2==0?store:replica).check(List.of(b));admitted.incrementAndGet();}catch(TooManyRequestsException expected){rejected.incrementAndGet();}
            }));}
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();for(var f:futures)f.get(20,TimeUnit.SECONDS);
        } finally {go.countDown();pool.shutdownNow();}
        assertEquals(8,admitted.get());assertEquals(16,rejected.get());
        assertThrows(TooManyRequestsException.class,()->new RequestBudgetStore(jdbc).check(List.of(b)));
    }
    @Test void rejectionDoesNotExtendTheWaitingPeriod() {
        var b=budget(1);store.check(List.of(b));long before=stored(b);
        for(int i=0;i<5;i++)assertThrows(TooManyRequestsException.class,()->store.check(List.of(b)));
        assertEquals(before,stored(b));
    }
    @Test void aRefilledSlotIsAvailableWithoutResettingOtherSlots() {
        var b=budget(2);store.check(List.of(b));store.check(List.of(b));
        assertThrows(TooManyRequestsException.class,()->store.check(List.of(b)));
        jdbc.update("UPDATE request_budgets SET next_at=next_at-1800000000 WHERE bucket_key=?",b.key());
        assertDoesNotThrow(()->store.check(List.of(b)));
        assertThrows(TooManyRequestsException.class,()->store.check(List.of(b)));
    }
    @Test void businessRollbackDoesNotRefundCredentialAttempts() {
        var b=budget(1);var transaction=new TransactionTemplate(manager);
        assertThrows(IllegalStateException.class,()->transaction.execute(s->{store.check(List.of(b));throw new IllegalStateException("rollback");}));
        assertThrows(TooManyRequestsException.class,()->store.check(List.of(b)));
    }
    @Test void deniedAccountStillConsumesTheEarlierIpBudget() {
        var account=budget(1);var ip=budget(2);store.check(List.of(account));
        assertThrows(TooManyRequestsException.class,()->store.check(List.of(ip,account)));
        store.check(List.of(ip));assertThrows(TooManyRequestsException.class,()->store.check(List.of(ip)));
    }
    @Test void rejectedResendsDoNotSpendTheLongIssuanceBudget() {
        String email=UUID.randomUUID()+"@example.test";
        limiter.anonymousAccount(RequestOperation.RESET_REQUEST,email);
        var hourly=RequestBudgetStore.budget("RESET_REQUEST","account",email,3,3600);long before=stored(hourly);
        for(int i=0;i<5;i++)assertThrows(TooManyRequestsException.class,()->limiter.anonymousAccount(RequestOperation.RESET_REQUEST,email));
        assertEquals(before,stored(hourly));
        String other=UUID.randomUUID()+"@example.test";
        limiter.request(RequestOperation.EMAIL_CHANGE_REQUEST,"192.0.2.1",other,false);
        var authenticated=RequestBudgetStore.budget("EMAIL_CHANGE_REQUEST","account",other,5,3600);long accepted=stored(authenticated);
        for(int i=0;i<5;i++)assertThrows(TooManyRequestsException.class,()->limiter.request(RequestOperation.EMAIL_CHANGE_REQUEST,"192.0.2.1",other,false));
        assertEquals(accepted,stored(authenticated));
    }
    @Test void accountsAndActionsRemainIndependent() {
        String id=UUID.randomUUID().toString();var login=RequestBudgetStore.budget("LOGIN","account",id,1,3600);
        store.check(List.of(login));assertThrows(TooManyRequestsException.class,()->store.check(List.of(login)));
        assertDoesNotThrow(()->store.check(List.of(RequestBudgetStore.budget("RESET_REQUEST","account",id,1,3600))));
        assertDoesNotThrow(()->store.check(List.of(RequestBudgetStore.budget("LOGIN","account",id+"other",1,3600))));
    }
    @Test void expiredCleanupSkipsLockedRowsAndNeverErasesAnActiveBucket() throws Exception {
        var old=budget(1);var active=budget(1);store.check(List.of(old));store.check(List.of(active));
        jdbc.update("UPDATE request_budgets SET next_at=0 WHERE bucket_key=?",old.key());
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var future=pool.submit(()->new TransactionTemplate(manager).execute(s->{
                jdbc.queryForObject("SELECT next_at FROM request_budgets WHERE bucket_key=? FOR UPDATE",Long.class,old.key());held.countDown();
                try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException(e);}return null;
            }));
            assertTrue(held.await(10,TimeUnit.SECONDS));store.prune();assertEquals(0,stored(old));assertTrue(stored(active)>0);
            release.countDown();future.get(10,TimeUnit.SECONDS);
        } finally {release.countDown();pool.shutdownNow();}
        store.prune();assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM request_budgets WHERE bucket_key=?",Integer.class,old.key()));
        assertTrue(stored(active)>0);
    }
    @Test void anonymousUnknownAccountVariantsHaveUniform429WithoutSendingMail() throws Exception {
        String email=UUID.randomUUID()+"@example.test";var mvc=mvc();
        mvc.perform(post("/api/auth/forgot-password").header("Origin","http://localhost:5173").param("email",email)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/forgot-password").header("Origin","http://localhost:5173").param("email"," "+email.toUpperCase(Locale.ROOT)+" "))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After")).andExpect(header().string("Cache-Control","no-store"));
        verifyNoInteractions(mail);
    }
    @Test void repeatedWrongPasswordsAreCountedBeforeAuthenticationWithoutIssuingCookies() throws Exception {
        String email=new TransactionTemplate(manager).execute(s->{
            String id=UUID.randomUUID().toString();var c=new Course();c.setLanguageCode(id.substring(0,8));c.setLanguageName("Rate test");em.persist(c);
            var u=new User();u.setName("Test");u.setEmail(id+"@example.test");u.setActiveCourse(c);u.setVerified(true);u.setPasswordHash(encoder.encode("Right-password"));em.persist(u);return u.getEmail();
        });
        var mvc=mvc();String body="{\"email\":\""+email+"\",\"password\":\"Wrong-password\"}";
        for(int i=0;i<20;i++)mvc.perform(post("/api/auth/login").header("Origin","http://localhost:5173").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").header("Origin","http://localhost:5173").contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After")).andExpect(header().doesNotExist("Set-Cookie"));
    }
    @Test void aClassroomOf1000DistinctAccountsIsNotLockedOutByItsSharedIp() {
        String ip=UUID.randomUUID().toString();
        for(int i=0;i<1000;i++) {
            limiter.request(RequestOperation.LOGIN,ip,null,false);
            limiter.anonymousAccount(RequestOperation.LOGIN,"class-"+i+UUID.randomUUID()+"@example.test");
        }
    }
}
