package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.security.*;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Never runs against the application's database; fixtures and all external effects are isolated. */
@EnabledIfEnvironmentVariable(named="SECURITY_FOUNDATIONS_TEST_DATABASE_URL", matches="jdbc:postgresql://[^/]+/languageapp_security_fix_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${SECURITY_FOUNDATIONS_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class DeviceSessionsPersistenceTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository records;
    @Autowired RefreshTokenService sessions;
    @Autowired AuthenticationService authentication;
    @Autowired OAuth2AuthenticationSuccessHandler oauthSuccess;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean SseService sse;
    @MockitoBean ImageStorageService images;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    MockMvc mvc;
    TransactionTemplate tx;
    record Login(String access, String refresh) { }

    @BeforeEach void setup() {
        tx=new TransactionTemplate(manager);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    User fixture() {
        return tx.execute(status -> {
            String key=UUID.randomUUID().toString().substring(0,8);
            var course=new Course();course.setLanguageCode(key);course.setLanguageName("Session test");em.persist(course);
            var user=new User();user.setName("Session "+key);user.setEmail(key+"@example.test");user.setVerified(true);
            user.setActiveCourse(course);user.setPasswordHash(encoder.encode("Session-old-password"));em.persist(user);em.flush();return user;
        });
    }
    Login login(User u) throws Exception {
        var response=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+u.getEmail()+"\",\"password\":\"Session-old-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String access=com.jayway.jsonpath.JsonPath.read(response.getContentAsString(),"$.accessToken");
        return new Login(access,response.getCookie("refreshToken").getValue());
    }
    String refresh(Login login) throws Exception {
        var response=mvc.perform(post("/api/auth/refresh").cookie(new MockCookie("refreshToken",login.refresh)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return com.jayway.jsonpath.JsonPath.read(response.getContentAsString(),"$.accessToken");
    }
    void profile(String token, int code) throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token)).andExpect(status().is(code));
    }
    void logout(Login login) throws Exception {
        mvc.perform(post("/api/auth/logout").cookie(new MockCookie("refreshToken",login.refresh)))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("refreshToken",0));
    }
    String signed(User user, Map<String,Object> claims) {
        var details=org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password("test-only").roles(user.getRole().name()).build();
        return jwt.generateToken(new HashMap<>(claims),details);
    }

    @Test void twoRealLoginsRemainIndependentAndLogoutRevokesOnlyTheSelectedAccessAndRefresh() throws Exception {
        var u=fixture();var first=login(u);var second=login(u);
        assertNotEquals(first.refresh,second.refresh);
        assertNotEquals(jwt.extractSessionId(first.access),jwt.extractSessionId(second.access));
        profile(first.access,200);profile(second.access,200);
        String renewedFirst=refresh(first);assertEquals(jwt.extractSessionId(first.access),jwt.extractSessionId(renewedFirst));
        logout(first);profile(first.access,401);profile(renewedFirst,401);
        mvc.perform(post("/api/auth/refresh").cookie(new MockCookie("refreshToken",first.refresh))).andExpect(status().isBadRequest());
        profile(second.access,200);profile(refresh(second),200);
        logout(first); // idempotent even when the original session is already gone
        profile(second.access,200);
    }
    @Test void resetRevokesPreviouslyIssuedAccessTokensAsWellAsBothRefreshSessions() throws Exception {
        var u=fixture();var first=login(u);var second=login(u);String reset=UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> {
            var token=new PasswordResetToken();token.setUser(em.getReference(User.class,u.getUserId()));
            token.setToken(reset);token.setExpiryDate(clock.instant().plusSeconds(60));em.persist(token);
        });
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\""+reset+"\",\"newPassword\":\"Session-new-password\"}")).andExpect(status().isOk());
        for(var old:List.of(first,second)) {
            profile(old.access,401);
            mvc.perform(post("/api/auth/refresh").cookie(new MockCookie("refreshToken",old.refresh))).andExpect(status().isBadRequest());
        }
    }
    @Test void sidlessMalformedUnknownAndOtherUsersSessionIdsCannotAuthenticate() throws Exception {
        var u=fixture();var other=fixture();var otherLogin=login(other);
        for(var claims:List.<Map<String,Object>>of(Map.of(),Map.of("sid","not-a-uuid"),Map.of("sid",UUID.randomUUID().toString()),
                Map.of("sid",jwt.extractSessionId(otherLogin.access).toString()))) profile(signed(u,claims),401);
        profile(otherLogin.access,200);
    }
    @Test void expiredSessionCannotBeUsedEvenWithAnUnexpiredAccessToken() throws Exception {
        var u=fixture();var login=login(u);
        tx.executeWithoutResult(status -> records.findById(jwt.extractSessionId(login.access)).orElseThrow().setExpiryDate(clock.instant()));
        profile(login.access,401);
        mvc.perform(post("/api/auth/refresh").cookie(new MockCookie("refreshToken",login.refresh))).andExpect(status().isBadRequest());
    }
    @Test void disabledAccountCannotRefreshOrUsePreviouslyIssuedAccess() throws Exception {
        var u=fixture();var login=login(u);
        tx.executeWithoutResult(status -> users.findById(u.getUserId()).orElseThrow().setActive(false));
        profile(login.access,401);
        mvc.perform(post("/api/auth/refresh").cookie(new MockCookie("refreshToken",login.refresh))).andExpect(status().isBadRequest());
        assertThrows(com.languageapp.backend.exception.BadRequestException.class,()->sessions.createSession(u.getUserId()));
    }
    @Test void authoritiesAreReloadedAndAnOldAdminClaimCannotBypassDemotion() throws Exception {
        var u=fixture();tx.executeWithoutResult(status -> users.findById(u.getUserId()).orElseThrow().setRole(Role.ADMIN));
        var login=login(u);
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+login.access)).andExpect(status().isOk());
        tx.executeWithoutResult(status -> users.findById(u.getUserId()).orElseThrow().setRole(Role.STUDENT));
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+login.access)).andExpect(status().isForbidden());
    }
    @Test void concurrentRefreshesInTwoTabsKeepOneStableSession() throws Exception {
        var login=login(fixture());var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            Callable<String> refresh=()->{assertTrue(start.await(5,TimeUnit.SECONDS));return authentication.refreshToken(login.refresh).getAccessToken();};
            var a=pool.submit(refresh);var b=pool.submit(refresh);start.countDown();
            for(var result:List.of(a,b)) {
                String access=result.get(15,TimeUnit.SECONDS);
                assertEquals(jwt.extractSessionId(login.access),jwt.extractSessionId(access));profile(access,200);
            }
        } finally {pool.shutdownNow();}
    }
    @Test void refreshIssuedBeforeConcurrentLogoutCannotRestoreARevokedSession() throws Exception {
        var login=login(fixture());String inFlight=refresh(login);
        logout(login);profile(inFlight,401);
        assertFalse(sessions.isSessionActive(jwt.extractSessionId(inFlight),jwt.extractUsername(inFlight)));
    }
    @Test void concurrentLogoutRequestsAreIdempotent() throws Exception {
        var login=login(fixture());var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            Callable<String> logout=()->{assertTrue(start.await(5,TimeUnit.SECONDS));return authentication.logout(login.refresh);};
            var a=pool.submit(logout);var b=pool.submit(logout);start.countDown();
            a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);
            profile(login.access,401);
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentConsumptionOfOneResetSucceedsExactlyOnce() throws Exception {
        var u=fixture();String reset=UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> {
            var token=new PasswordResetToken();token.setUser(em.getReference(User.class,u.getUserId()));
            token.setToken(reset);token.setExpiryDate(clock.instant().plusSeconds(60));em.persist(token);
        });
        var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            Callable<Boolean> consume=()->{
                assertTrue(start.await(5,TimeUnit.SECONDS));
                try {authentication.resetPassword(reset,"Session-new-password");return true;}
                catch(com.languageapp.backend.exception.BadRequestException used) {return false;}
            };
            var a=pool.submit(consume);var b=pool.submit(consume);start.countDown();
            assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertTrue(encoder.matches("Session-new-password",users.findById(u.getUserId()).orElseThrow().getPasswordHash()));
        } finally {pool.shutdownNow();}
    }
    @Test void aLoginBlockedBehindResetRechecksTheNewPasswordAndCannotUndoTheReset() throws Exception {
        var u=fixture();String reset=UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> {
            var token=new PasswordResetToken();token.setUser(em.getReference(User.class,u.getUserId()));
            token.setToken(reset);token.setExpiryDate(clock.instant().plusSeconds(60));em.persist(token);
        });
        var pool=Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> blocked=tx.execute(status -> {
                authentication.resetPassword(reset,"Session-new-password");
                Future<Boolean> login=pool.submit(()-> {
                    var request=new com.languageapp.backend.dto.request.LoginRequest();
                    request.setEmail(u.getEmail());request.setPassword("Session-old-password");
                    try {authentication.authenticate(request);return true;}
                    catch(org.springframework.security.authentication.BadCredentialsException rejected) {return false;}
                });
                // Observe a real PostgreSQL lock wait rather than assuming the worker reached the query.
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<until) {
                    Number count=(Number)em.createNativeQuery("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'").getSingleResult();
                    if(count.longValue()>0) {waiting=true;break;}
                    try {Thread.sleep(20);} catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                }
                assertTrue(waiting,"The competing login must reach the database lock before reset commits");
                return login;
            });
            assertFalse(blocked.get(15,TimeUnit.SECONDS));
            assertTrue(encoder.matches("Session-new-password",users.findById(u.getUserId()).orElseThrow().getPasswordHash()));
        } finally {pool.shutdownNow();}
    }
    @Test void oauthSuccessIssuesASessionBoundTokenWithoutReplacingAnExistingDevice() throws Exception {
        var u=fixture();var previous=login(u);
        var principal=new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email",u.getEmail(),"sub","test-subject"),"sub");
        var response=new MockHttpServletResponse();
        oauthSuccess.onAuthenticationSuccess(new MockHttpServletRequest(),response,
                new UsernamePasswordAuthenticationToken(principal,"unused",principal.getAuthorities()));
        String redirected=response.getRedirectedUrl();assertNotNull(redirected);
        String access=redirected.substring(redirected.indexOf("?token=")+7);
        assertNotNull(jwt.extractSessionId(access));assertNotEquals(jwt.extractSessionId(previous.access),jwt.extractSessionId(access));
        profile(previous.access,200);profile(access,200);
        assertTrue(response.getHeader("Set-Cookie").contains("HttpOnly"));
    }
}
