package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.security.*;
import com.languageapp.backend.service.*;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses actual Spring OAuth code exchange, loopback-only provider and disposable PostgreSQL. */
@EnabledIfEnvironmentVariable(named="OAUTH_LINK_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_oauth_link_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${OAUTH_LINK_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.show-sql=false","jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class OAuthLinkPersistenceTest {
    @Autowired OAuthLinkService links;
    @Autowired VerificationEmailService verificationMail;
    @Autowired VerificationTokenRepository verificationTokens;
    @Autowired AuthenticationService authentication;
    @Autowired UserService profiles;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired FederatedIdentityService federated;
    @Autowired OAuthLinkIntentRepository intents;
    @Autowired OAuthIdentityRepository identities;
    @Autowired UserRepository users;
    @Autowired RefreshTokenService sessions;
    @Autowired RefreshTokenRepository sessionRows;
    @Autowired JwtService jwt;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired WebApplicationContext context;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @MockitoBean ClientRegistrationRepository registrations;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;
    MockMvc mvc;
    HttpServer server;
    final AtomicReference<Map<String,Object>> claims=new AtomicReference<>();
    final Map<String,String> codes=new ConcurrentHashMap<>();
    record Account(User user,RefreshTokenService.Session session,String access) { }
    record Started(MockHttpSession browser,String state,String provider) { }
    @BeforeEach void setup() throws Exception {
        tx=new TransactionTemplate(manager);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        doAnswer(call -> { codes.put(call.getArgument(0),call.getArgument(2));return null; }).when(mail).sendOAuthLinkCode(anyString(),anyString(),anyString());
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/token",exchange->{
            byte[] body="{\"access_token\":\"loopback-only-token\",\"token_type\":\"Bearer\",\"expires_in\":300}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/userinfo",exchange->{
            byte[] body=json.writeValueAsBytes(claims.get());
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        for(String provider:List.of("google","discord")) {
            String endpoint="http://127.0.0.1:"+server.getAddress().getPort();
            when(registrations.findByRegistrationId(provider)).thenReturn(ClientRegistration.withRegistrationId(provider)
                    .clientId("test-client").clientSecret("test-secret").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").authorizationUri(endpoint+"/authorize")
                    .tokenUri(endpoint+"/token").userInfoUri(endpoint+"/userinfo").userNameAttributeName(provider.equals("google")?"sub":"id")
                    .scope("email").build());
        }
    }
    @AfterEach void cleanup(){if(server!=null)server.stop(0);}
    Account account(){
        User user=tx.execute(status->{User u=new User();u.setName("Chosen profile");u.setEmail(UUID.randomUUID()+"@example.test");
            u.setVerified(true);u.setPasswordHash("test-only-hash");u.setXp(37);u.setRole(Role.TEACHER);em.persist(u);em.flush();return u;});
        var session=sessions.createSession(user.getUserId());
        String access=jwt.generateSessionToken(org.springframework.security.core.userdetails.User.withUsername(user.getEmail()).password("").roles("TEACHER").build(),session.id());
        return new Account(user,session,access);
    }
    String nonce(Account a,String provider){
        links.request(a.user.getEmail(),a.session.id(),a.session.rawToken(),provider);
        assertNotNull(codes.get(a.user.getEmail()));
        var auth=links.verify(a.user.getEmail(),a.session.id(),a.session.rawToken(),codes.get(a.user.getEmail()));
        return auth.authorizationUrl().substring(auth.authorizationUrl().indexOf("?link=")+6);
    }
    Started start(Account a,String provider)throws Exception {
        String nonce=nonce(a,provider);
        var result=mvc.perform(get("/oauth2/authorization/"+provider).param("link",nonce)).andExpect(status().is3xxRedirection()).andReturn();
        String state=UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build().getQueryParams().getFirst("state");
        assertNotNull(state);state=URLDecoder.decode(state,java.nio.charset.StandardCharsets.UTF_8);return new Started((MockHttpSession)result.getRequest().getSession(false),state,provider);
    }
    String subject(){return "1"+String.valueOf(Math.abs(UUID.randomUUID().getLeastSignificantBits())).substring(0,12);}
    Map<String,Object> claims(String provider,String subject,boolean verified){
        return Map.of(provider.equals("google")?"sub":"id",subject,provider.equals("google")?"email_verified":"verified",verified,
                "email","provider-"+subject+"@example.test",provider.equals("google")?"name":"username","Provider name");
    }
    MockHttpServletResponse callback(Started started,String subject,boolean verified)throws Exception {
        claims.set(claims(started.provider,subject,verified));
        return mvc.perform(get("/login/oauth2/code/"+started.provider).session(started.browser).param("code","test-code").param("state",started.state))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
    }
    String proof(Account a,String provider,String subject)throws Exception {
        var result=callback(start(a,provider),subject,true);
        assertNull(result.getHeader("Set-Cookie"));assertTrue(result.getRedirectedUrl().contains("/oauth-link/confirm#proof="),result.getRedirectedUrl());
        return result.getRedirectedUrl().split("#proof=")[1];
    }
    OAuthLinkService.Preview confirm(Account a,String proof){return links.confirm(a.user.getEmail(),a.session.id(),a.session.rawToken(),proof,true);}
    @ParameterizedTest @ValueSource(strings={"google","discord"})
    void fullFilterExchangeRequiresFinalOriginalSessionConfirmationAndKeepsProfile(String provider)throws Exception {
        Account a=account();String subject=subject();long count=users.count();String proof=proof(a,provider,subject);
        assertEquals(count,users.count());assertTrue(identities.findUserId(AuthProvider.valueOf(provider.toUpperCase()),subject).isEmpty());
        assertEquals(1,sessionRows.findAll().stream().filter(row -> row.getUser().getUserId().equals(a.user.getUserId())).count());
        mvc.perform(post("/api/oauth-links/preview").header("Authorization","Bearer "+a.access)
                .cookie(new MockCookie("refreshToken",a.session.rawToken())).contentType("application/json").content(json.writeValueAsString(Map.of("proof",proof))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        assertFalse(links.preview(a.user.getEmail(),a.session.id(),a.session.rawToken(),proof).linked());
        assertTrue(confirm(a,proof).linked());assertTrue(confirm(a,proof).linked());
        assertEquals(a.user.getUserId(),federated.bind(new OAuthClaims(AuthProvider.valueOf(provider.toUpperCase()),subject,"changed@example.test","Changed",null)).userId());
        User retained=users.findById(a.user.getUserId()).orElseThrow();assertEquals("Chosen profile",retained.getName());assertEquals(a.user.getEmail(),retained.getEmail());
        assertEquals(37,retained.getXp());assertEquals(Role.TEACHER,retained.getRole());
        verify(mail,times(1)).sendOAuthLinkSecurityAlert(eq(a.user.getEmail()),anyString());
        var normal=mvc.perform(get("/oauth2/authorization/"+provider)).andExpect(status().is3xxRedirection()).andReturn();
        String normalState=URLDecoder.decode(UriComponentsBuilder.fromUriString(normal.getResponse().getRedirectedUrl()).build().getQueryParams().getFirst("state"),java.nio.charset.StandardCharsets.UTF_8);
        var login=callback(new Started((MockHttpSession)normal.getRequest().getSession(false),normalState,provider),subject,true);
        assertTrue(login.getRedirectedUrl().contains("/oauth2/redirect#code="));assertNull(login.getHeader("Set-Cookie"));
        String handoff=login.getRedirectedUrl().split("#code=")[1];
        var redeemed=mvc.perform(post("/api/auth/oauth/exchange").session((MockHttpSession)normal.getRequest().getSession(false))
                .header("Origin","http://localhost:5173").contentType("application/json").content(json.writeValueAsString(Map.of("code",handoff))))
                .andExpect(status().isOk()).andReturn().getResponse();
        String access=com.jayway.jsonpath.JsonPath.read(redeemed.getContentAsString(),"$.accessToken");assertEquals(a.user.getEmail(),jwt.extractUsername(access));
    }
    @Test void originalAndProviderEmailsNeedNotMatchButAnExistingProviderOwnerCannotBeMerged()throws Exception {
        Account owner=account(),other=account();String subject=subject();confirm(owner,proof(owner,"google",subject));
        var result=callback(start(other,"google"),subject,true);
        assertTrue(result.getRedirectedUrl().contains("oauth_link_error"));
        assertEquals(owner.user.getUserId(),identities.findUserId(AuthProvider.GOOGLE,subject).orElseThrow());
    }
    @Test void legacyProviderOwnershipIsAlsoRejectedBeforeAnyIdentityExists()throws Exception {
        Account legacy=account(),other=account();String subject=subject();tx.executeWithoutResult(s->{var u=users.findForSessionUpdate(legacy.user.getUserId()).orElseThrow();u.setAuthProvider(AuthProvider.GOOGLE);u.setProviderId(subject);});
        var result=callback(start(other,"google"),subject,true);assertTrue(result.getRedirectedUrl().contains("oauth_link_error"));
        assertTrue(identities.findUserId(AuthProvider.GOOGLE,subject).isEmpty());
    }
    @Test void invalidAuthorizationStartReturnsToSettingsWithoutLoggingIn()throws Exception {
        mvc.perform(get("/oauth2/authorization/google").param("link","invalid"))
                .andExpect(status().is3xxRedirection()).andExpect(result -> assertTrue(result.getResponse().getRedirectedUrl().endsWith("/settings?oauth_link_error=failed")));
    }
    @Test void absentOrUnverifiedClaimsNeverLink()throws Exception {
        Account a=account();var result=callback(start(a,"google"),subject(),false);
        assertTrue(result.getRedirectedUrl().contains("oauth_link_error"));assertTrue(identities.findByUserUserId(a.user.getUserId()).isEmpty());
    }
    @Test void invalidStateCannotReachProviderOrCreateProof()throws Exception {
        Account a=account();var started=start(a,"google");
        mvc.perform(get("/login/oauth2/code/google").session(started.browser).param("code","test-code").param("state","wrong-state"))
                .andExpect(status().is3xxRedirection());
        assertNull(intents.findById(a.user.getUserId()).orElseThrow().getConfirmationHash());
    }
    @Test void finalProofCannotBeUsedByOtherAccountOrAnotherSessionOrWithoutCookie()throws Exception {
        Account a=account(),other=account();String proof=proof(a,"google",subject());var second=sessions.createSession(a.user.getUserId());
        assertThrows(RuntimeException.class,()->confirm(other,proof));
        assertThrows(RuntimeException.class,()->links.confirm(a.user.getEmail(),second.id(),second.rawToken(),proof,true));
        assertThrows(RuntimeException.class,()->links.confirm(a.user.getEmail(),a.session.id(),null,proof,true));
        assertThrows(RuntimeException.class,()->links.confirm(a.user.getEmail(),a.session.id(),a.session.rawToken(),proof,false));
        assertTrue(identities.findByUserUserId(a.user.getUserId()).isEmpty());assertTrue(confirm(a,proof).linked());
    }
    @Test void revokedSessionInvalidatesAlreadyVerifiedProvider()throws Exception {
        Account a=account();String proof=proof(a,"google",subject());tx.executeWithoutResult(s->sessionRows.deleteById(a.session.id()));
        assertThrows(RuntimeException.class,()->confirm(a,proof));assertTrue(identities.findByUserUserId(a.user.getUserId()).isEmpty());
    }
    @Test void changedAccountSecurityInvalidatesProof()throws Exception {
        Account a=account();String proof=proof(a,"google",subject());tx.executeWithoutResult(s->{var u=users.findForSessionUpdate(a.user.getUserId()).orElseThrow();u.setPasswordHash("different");});
        assertThrows(RuntimeException.class,()->confirm(a,proof));
    }
    @Test void cancelledOrExpiredProofCannotBeConfirmed()throws Exception {
        Account a=account();String proof=proof(a,"google",subject());links.cancel(a.user.getEmail(),a.session.id(),a.session.rawToken());assertThrows(RuntimeException.class,()->confirm(a,proof));
        Account b=account();String expired=proof(b,"google",subject());tx.executeWithoutResult(s->intents.findById(b.user.getUserId()).orElseThrow().setExpiresAt(Instant.EPOCH));
        assertThrows(RuntimeException.class,()->confirm(b,expired));
    }
    @Test void oneTimeAuthorizationNonceAndCallbackCannotBeReplayed()throws Exception {
        Account a=account();String nonce=nonce(a,"google");
        links.startAuthorization(nonce,"google","state");assertThrows(RuntimeException.class,()->links.startAuthorization(nonce,"google","state"));
        Account b=account();var started=start(b,"google");var first=callback(started,subject(),true);assertTrue(first.getRedirectedUrl().contains("#proof="));
        var second=callback(started,subject(),true);assertFalse(second.getRedirectedUrl().contains("#proof="));
    }
    @Test void fiveWrongCodesPersistAcrossActualHttp400Responses()throws Exception {
        Account a=account();links.request(a.user.getEmail(),a.session.id(),a.session.rawToken(),"google");
        String wrong="000000".equals(codes.get(a.user.getEmail()))?"111111":"000000";
        for(int n=1;n<=5;n++) {
            mvc.perform(post("/api/oauth-links/verify").header("Authorization","Bearer "+a.access).cookie(new MockCookie("refreshToken",a.session.rawToken()))
                    .contentType("application/json").content(json.writeValueAsString(Map.of("code",wrong)))).andExpect(status().isBadRequest());
            assertEquals(n,intents.findById(a.user.getUserId()).orElseThrow().getFailedAttempts());
        }
        assertThrows(RuntimeException.class,()->links.verify(a.user.getEmail(),a.session.id(),a.session.rawToken(),codes.get(a.user.getEmail())));
    }
    @Test void requestCooldownAndHourlyBudgetSurviveCancellation() {
        Account a=account();links.request(a.user.getEmail(),a.session.id(),a.session.rawToken(),"google");
        links.cancel(a.user.getEmail(),a.session.id(),a.session.rawToken());
        assertThrows(RuntimeException.class,()->links.request(a.user.getEmail(),a.session.id(),a.session.rawToken(),"discord"));
        for(int n=2;n<=5;n++) {tx.executeWithoutResult(s->intents.findById(a.user.getUserId()).orElseThrow().setLastRequestedAt(Instant.EPOCH));links.request(a.user.getEmail(),a.session.id(),a.session.rawToken(),"google");}
        tx.executeWithoutResult(s->intents.findById(a.user.getUserId()).orElseThrow().setLastRequestedAt(Instant.EPOCH));
        assertThrows(RuntimeException.class,()->links.request(a.user.getEmail(),a.session.id(),a.session.rawToken(),"google"));
    }
    @Test void replacementRequestInvalidatesOldCodeAndProof()throws Exception {
        Account a=account();String proof=proof(a,"google",subject());tx.executeWithoutResult(s->intents.findById(a.user.getUserId()).orElseThrow().setLastRequestedAt(Instant.EPOCH));
        links.request(a.user.getEmail(),a.session.id(),a.session.rawToken(),"discord");assertThrows(RuntimeException.class,()->confirm(a,proof));
    }
    @Test void concurrentFinalConfirmationsForOneProviderIdentityHaveExactlyOneOwner()throws Exception {
        Account first=account(),second=account();String subject=subject();String p1=proof(first,"google",subject),p2=proof(second,"google",subject);
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try {
            Callable<Boolean> one=()->{ready.countDown();go.await(5,TimeUnit.SECONDS);try{confirm(first,p1);return true;}catch(RuntimeException conflict){return false;}};
            Callable<Boolean> two=()->{ready.countDown();go.await(5,TimeUnit.SECONDS);try{confirm(second,p2);return true;}catch(RuntimeException conflict){return false;}};
            var f1=pool.submit(one);var f2=pool.submit(two);assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();
            assertNotEquals(f1.get(10,TimeUnit.SECONDS),f2.get(10,TimeUnit.SECONDS));
            assertTrue(identities.findUserId(AuthProvider.GOOGLE,subject).isPresent());
        }finally{pool.shutdownNow();}
    }
    @Test void completedLinkAndPendingSecretsAreRemovedOnAccountAnonymization()throws Exception {
        Account a=account();confirm(a,proof(a,"google",subject()));
        tx.executeWithoutResult(s->users.findForSessionUpdate(a.user.getUserId()).orElseThrow().setPasswordHash(encoder.encode("local-test-only-password")));
        profiles.deleteAndAnonymizeMyAccount(a.user.getEmail(),"local-test-only-password",true);
        assertTrue(intents.findById(a.user.getUserId()).isEmpty());assertTrue(identities.findByUserUserId(a.user.getUserId()).isEmpty());
    }
    @Test void wrongSessionAndProviderCannotRedeemTheAuthorizationOrVerification() {
        Account a=account();links.request(a.user.getEmail(),a.session.id(),a.session.rawToken(),"google");var other=sessions.createSession(a.user.getUserId());
        assertThrows(RuntimeException.class,()->links.verify(a.user.getEmail(),other.id(),other.rawToken(),codes.get(a.user.getEmail())));
        String url=links.verify(a.user.getEmail(),a.session.id(),a.session.rawToken(),codes.get(a.user.getEmail())).authorizationUrl();
        String nonce=url.substring(url.indexOf("?link=")+6);
        assertThrows(RuntimeException.class,()->links.startAuthorization(nonce,"discord","state"));
        assertNotNull(links.startAuthorization(nonce,"google","state"));
    }
    @Test void twoParallelCallbacksCannotBothConsumeTheSameAuthorizationNonce()throws Exception {
        Account a=account();String nonce=nonce(a,"google");var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        Callable<Boolean> redeem=()->{go.await();try{links.startAuthorization(nonce,"google","state");return true;}catch(RuntimeException duplicate){return false;}};
        try{var one=pool.submit(redeem);var two=pool.submit(redeem);go.countDown();assertNotEquals(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
    }
    Account unverified(){
        Account a=account();tx.executeWithoutResult(s->users.findForSessionUpdate(a.user.getUserId()).orElseThrow().setVerified(false));return a;
    }
    @Test void authenticatedResendVerifiesOnlyTheCurrentAccountThroughTheNewMail()throws Exception {
        Account a=unverified();
        mvc.perform(post("/api/users/me/verification-email")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/me/verification-email").header("Authorization","Bearer "+a.access))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","no-store"));
        var code=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).sendVerificationEmail(eq(a.user.getEmail()),eq(a.user.getName()),code.capture());
        assertFalse(users.findById(a.user.getUserId()).orElseThrow().isVerified());
        authentication.verifyEmail(code.getValue());assertTrue(users.findById(a.user.getUserId()).orElseThrow().isVerified());
        assertThrows(RuntimeException.class,()->verificationMail.resend(a.user.getEmail()));
    }
    @Test void resendInvalidatesEarlierLinksButRollbackKeepsTheOriginalUsable() {
        Account a=unverified();String old=UUID.randomUUID().toString();
        tx.executeWithoutResult(s->{var token=new VerificationToken();token.setUser(users.findById(a.user.getUserId()).orElseThrow());token.setToken(old);token.setExpiryDate(java.time.Instant.now().plusSeconds(3600));em.persist(token);em.flush();
            em.createNativeQuery("update verification_tokens set created_at=current_timestamp-interval '2 minutes' where token=:token").setParameter("token",old).executeUpdate();});
        tx.executeWithoutResult(s->{verificationMail.resend(a.user.getEmail());s.setRollbackOnly();});
        assertTrue(verificationTokens.findByToken(old).orElseThrow().getExpiryDate().isAfter(java.time.Instant.now()));
        verify(mail,never()).sendVerificationEmail(eq(a.user.getEmail()),anyString(),anyString());
        verificationMail.resend(a.user.getEmail());assertThrows(RuntimeException.class,()->authentication.verifyEmail(old));
        assertThrows(RuntimeException.class,()->verificationMail.resend(a.user.getEmail()));
    }
    @Test void concurrentResendsHaveOneWinnerAndTheHourlyLimitPersists()throws Exception {
        Account a=unverified();var pool=Executors.newFixedThreadPool(4);var go=new CountDownLatch(1);
        Callable<Boolean> send=()->{go.await();try{verificationMail.resend(a.user.getEmail());return true;}catch(com.languageapp.backend.exception.BadRequestException limited){return false;}};
        try{var futures=new ArrayList<Future<Boolean>>();for(int i=0;i<4;i++)futures.add(pool.submit(send));go.countDown();int wins=0;for(var future:futures)if(future.get(10,TimeUnit.SECONDS))wins++;assertEquals(1,wins);}
        finally{pool.shutdownNow();}
        for(int i=1;i<5;i++) {
            tx.executeWithoutResult(s->em.createNativeQuery("update verification_tokens set created_at=current_timestamp-interval '2 minutes' where user_id=:id").setParameter("id",a.user.getUserId()).executeUpdate());
            verificationMail.resend(a.user.getEmail());
        }
        tx.executeWithoutResult(s->em.createNativeQuery("update verification_tokens set created_at=current_timestamp-interval '2 minutes' where user_id=:id").setParameter("id",a.user.getUserId()).executeUpdate());
        assertThrows(RuntimeException.class,()->verificationMail.resend(a.user.getEmail()));
    }
    @Test void rollbackDoesNotSendCodeOrPersistIntent() {
        Account a=account();tx.executeWithoutResult(status->{links.request(a.user.getEmail(),a.session.id(),a.session.rawToken(),"google");status.setRollbackOnly();});
        assertTrue(intents.findById(a.user.getUserId()).isEmpty());verify(mail,never()).sendOAuthLinkCode(eq(a.user.getEmail()),anyString(),anyString());
    }
}
