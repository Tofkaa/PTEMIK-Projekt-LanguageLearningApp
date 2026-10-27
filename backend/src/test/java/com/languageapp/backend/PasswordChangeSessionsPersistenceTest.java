package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.dto.request.LoginRequest;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.AuthProvider;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.security.*;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="PASSWORD_CHANGE_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_password_change_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${PASSWORD_CHANGE_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create","spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class PasswordChangeSessionsPersistenceTest {
    @Autowired UserService service;
    @Autowired AuthenticationService authentication;
    @Autowired RefreshTokenService refresh;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PasswordEncoder encoder;
    @Autowired WebApplicationContext context;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean SseService sse;
    @MockitoBean ImageStorageService images;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;
    static final String OLD="Original-password-123",NEW="Changed-password-456";
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);}
    record Fixture(UUID id,String email) { }
    Fixture fixture(){return tx.execute(status -> {
        String key=UUID.randomUUID().toString().substring(0,8);Course c=new Course();c.setLanguageCode(key);c.setLanguageName("Password change");em.persist(c);
        User u=new User();u.setName(key);u.setEmail(key+"@example.test");u.setActiveCourse(c);u.setVerified(true);u.setFriendCode(key);u.setUserTag("1234");u.setPasswordHash(encoder.encode(OLD));em.persist(u);
        return new Fixture(u.getUserId(),u.getEmail());
    });}
    String token(Fixture f,RefreshTokenService.Session s){
        var details=org.springframework.security.core.userdetails.User.withUsername(f.email()).password("unused").roles("STUDENT").build();
        return "Bearer "+jwt.generateSessionToken(details,s.id());
    }
    long count(Fixture f){return tx.execute(status -> em.createQuery("select count(t) from RefreshToken t where t.user.userId=:id",Long.class).setParameter("id",f.id()).getSingleResult());}
    LoginRequest login(Fixture f,String password){LoginRequest r=new LoginRequest();r.setEmail(f.email());r.setPassword(password);return r;}
    void awaitBlocked(int expected){
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<end){Number n=(Number)em.createNativeQuery("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and cardinality(pg_blocking_pids(pid))>0").getSingleResult();if(n.intValue()>=expected)return;
            try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
        fail("Credential operation must be waiting for the account lock");
    }
    @Test void httpChangeRevokesAllOwnAccessAndRefreshSessionsButNotAnotherAccount() throws Exception {
        Fixture f=fixture(),other=fixture();var current=refresh.createSession(f.id());var second=refresh.createSession(f.id());var unrelated=refresh.createSession(other.id());
        String currentJwt=token(f,current),secondJwt=token(f,second),otherJwt=token(other,unrelated);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(put("/api/users/me/password").header("Authorization",currentJwt).contentType("application/json")
                .content("{\"currentPassword\":\""+OLD+"\",\"newPassword\":\""+NEW+"\"}")).andExpect(status().isOk());
        assertEquals(0,count(f));assertEquals(1,count(other));
        for(String access:List.of(currentJwt,secondJwt))mvc.perform(get("/api/users/me").header("Authorization",access)).andExpect(status().isUnauthorized());
        for(String raw:List.of(current.rawToken(),second.rawToken()))mvc.perform(post("/api/auth/refresh").cookie(new MockCookie("refreshToken",raw))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/me").header("Authorization",otherJwt)).andExpect(status().isOk());
        assertThrows(BadCredentialsException.class,()->authentication.authenticate(login(f,OLD)));
        assertNotNull(authentication.authenticate(login(f,NEW)).responseDto().getAccessToken());assertEquals(1,count(f));
        mvc.perform(post("/api/auth/logout").cookie(new MockCookie("refreshToken",current.rawToken())))
                .andExpect(status().isOk()).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Max-Age=0")));
    }
    @Test void rejectedCurrentOrNewPasswordLeavesCredentialsAndSessionsIntact(){
        Fixture f=fixture();var one=refresh.createSession(f.id());var two=refresh.createSession(f.id());
        assertThrows(BadRequestException.class,()->service.changePassword(f.email(),"wrong",NEW));
        assertThrows(BadRequestException.class,()->service.changePassword(f.email(),OLD,"short"));
        assertThrows(BadRequestException.class,()->service.changePassword(f.email(),OLD,OLD));
        assertTrue(encoder.matches(OLD,users.findById(f.id()).orElseThrow().getPasswordHash()));assertEquals(2,count(f));
        assertTrue(refresh.isSessionActive(one.id(),f.email()));assertTrue(refresh.isSessionActive(two.id(),f.email()));
    }
    @Test void outerRollbackRestoresPasswordAndRevokedSessionsTogether(){
        Fixture f=fixture();refresh.createSession(f.id());
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status -> {service.changePassword(f.email(),OLD,NEW);assertEquals(0,count(f));throw new IllegalStateException("rollback fixture");}));
        assertEquals(1,count(f));assertTrue(encoder.matches(OLD,users.findById(f.id()).orElseThrow().getPasswordHash()));
    }
    @Test void parallelChangesMustRecheckTheCurrentPasswordAfterObtainingTheAccountLock() throws Exception {
        Fixture f=fixture();refresh.createSession(f.id());var workers=Executors.newFixedThreadPool(2);List<Future<Boolean>> attempts=new ArrayList<>();
        try {
            tx.executeWithoutResult(status -> {
                users.findByEmailForAuthentication(f.email()).orElseThrow();
                for(int i=0;i<2;i++)attempts.add(workers.submit(()->{try{service.changePassword(f.email(),OLD,NEW);return true;}catch(BadRequestException expected){return false;}}));
                awaitBlocked(2);
            });
            assertNotEquals(attempts.get(0).get(15,TimeUnit.SECONDS),attempts.get(1).get(15,TimeUnit.SECONDS));assertEquals(0,count(f));
            assertTrue(encoder.matches(NEW,users.findById(f.id()).orElseThrow().getPasswordHash()));
        } finally {workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void loginWaitingOnPasswordChangeCannotCreateASessionUsingTheOldPassword() throws Exception {
        Fixture f=fixture();var worker=Executors.newSingleThreadExecutor();List<Future<Boolean>> attempt=new ArrayList<>();
        try {
            tx.executeWithoutResult(status -> {
                service.changePassword(f.email(),OLD,NEW);
                attempt.add(worker.submit(()->{try{authentication.authenticate(login(f,OLD));return true;}catch(BadCredentialsException expected){return false;}}));
                awaitBlocked(1);
            });
            assertFalse(attempt.getFirst().get(15,TimeUnit.SECONDS));assertEquals(0,count(f));
        } finally {worker.shutdownNow();assertTrue(worker.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void passwordlessOAuthAccountCannotUseTheLocalPasswordChangeToSetACredential(){
        Fixture f=fixture();var session=refresh.createSession(f.id());
        tx.executeWithoutResult(status -> {User u=users.findById(f.id()).orElseThrow();u.setPasswordHash(null);u.setAuthProvider(AuthProvider.GOOGLE);u.setProviderId("synthetic-subject");});
        assertThrows(BadRequestException.class,()->service.changePassword(f.email(),OLD,NEW));
        assertNull(users.findById(f.id()).orElseThrow().getPasswordHash());assertTrue(refresh.isSessionActive(session.id(),f.email()));
    }
}
