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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Loopback provider only; all schemas are confined to the explicitly opted-in disposable database. */
@EnabledIfEnvironmentVariable(named="OAUTH_IDENTITY_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_oauth_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${OAUTH_IDENTITY_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.show-sql=false","jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class FederatedIdentityPersistenceTest {
    @Autowired FederatedIdentityService federated;
    @Autowired CustomOAuth2UserService oauth;
    @Autowired OAuth2AuthenticationSuccessHandler success;
    @Autowired UserRepository users;
    @Autowired OAuthIdentityRepository identities;
    @Autowired CourseRepository courses;
    @Autowired RefreshTokenRepository sessions;
    @Autowired JwtService jwt;
    @Autowired EntityManager em;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;
    HttpServer provider;
    RestOperations originalClient;
    final AtomicReference<Map<String,Object>> response=new AtomicReference<>();
    @BeforeEach void setup() throws Exception {
        tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(status->{
            if(courses.findByLanguageCode("en").isEmpty()) {
                Course course=new Course();course.setLanguageCode("en");course.setLanguageName("English");em.persist(course);
            }
        });
        originalClient=(RestOperations)ReflectionTestUtils.getField(oauth,"restOperations");
        provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        provider.createContext("/userinfo",exchange->{
            if(!"Bearer provider-test-access".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401,-1);exchange.close();return;
            }
            byte[] body=json.writeValueAsBytes(response.get());
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });
        provider.start();
    }
    @AfterEach void cleanup() { provider.stop(0);oauth.setRestOperations(originalClient); }
    String subject() { return String.valueOf(Math.abs(UUID.randomUUID().getLeastSignificantBits())%100000000000000000L+100000000000000000L); }
    String email() { return "oauth-"+UUID.randomUUID()+"@example.test"; }
    Map<String,Object> attributes(String provider,String subject,String email) {
        Map<String,Object> attrs=new HashMap<>();attrs.put(provider.equals("google")?"sub":"id",subject);
        attrs.put(provider.equals("google")?"email_verified":"verified",true);attrs.put("email",email);
        attrs.put(provider.equals("google")?"name":"username","Provider display name");return attrs;
    }
    OAuth2UserRequest request(String name) {
        String endpoint="http://127.0.0.1:"+provider.getAddress().getPort();
        var registration=ClientRegistration.withRegistrationId(name).clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/callback")
                .authorizationUri(endpoint+"/authorize").tokenUri(endpoint+"/token").userInfoUri(endpoint+"/userinfo")
                .userNameAttributeName(name.equals("google")?"sub":"id").build();
        return new OAuth2UserRequest(registration,new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"provider-test-access",null,null));
    }
    FederatedOAuth2User fetch(String provider,String subject,String email) {
        response.set(attributes(provider,subject,email));return (FederatedOAuth2User)oauth.loadUser(request(provider));
    }
    User account(AuthProvider provider,String subject,String email) {
        return tx.execute(status->{
            User user=new User();user.setEmail(email);user.setName("Existing chosen name");user.setAuthProvider(provider);
            user.setProviderId(subject);user.setVerified(true);user.setXp(37);user.setRole(Role.TEACHER);
            user.setActiveCourse(courses.findByLanguageCode("en").orElseThrow());user.setUserTag("1234");
            user.setFriendCode(UUID.randomUUID().toString().substring(0,8));em.persist(user);em.flush();return user;
        });
    }
    @ParameterizedTest @ValueSource(strings={"google","discord"})
    void verifiedProviderCanEnrollAndObtainASessionBoundToItsNewStudent(String provider) throws Exception {
        String email=email(),subject=subject();var principal=fetch(provider,subject,email);
        User user=users.findById(principal.userId()).orElseThrow();assertEquals(Role.STUDENT,user.getRole());assertTrue(user.isVerified());
        assertEquals(user.getUserId(),identities.findUserId(AuthProvider.valueOf(provider.toUpperCase(Locale.ROOT)),subject).orElseThrow());
        var result=new MockHttpServletResponse();
        success.onAuthenticationSuccess(new MockHttpServletRequest(),result,new OAuth2AuthenticationToken(principal,principal.getAuthorities(),provider));
        String url=result.getRedirectedUrl();assertNotNull(url);String token=url.substring(url.indexOf("?token=")+7);
        assertEquals(user.getEmail(),jwt.extractUsername(token));assertNotNull(jwt.extractSessionId(token));
        assertTrue(result.getHeader("Set-Cookie").contains("HttpOnly"));
    }
    @ParameterizedTest @ValueSource(strings={"google","discord"})
    void verifiedProviderCannotTakeOverALocalAccountWithTheSameEmail(String provider) {
        String email=email();User local=account(AuthProvider.LOCAL,null,email.toUpperCase(Locale.ROOT));
        long sessionsBefore=sessions.count();
        var rejected=assertThrows(OAuth2AuthenticationException.class,()->fetch(provider,subject(),email));
        assertEquals("email_in_use",rejected.getError().getErrorCode());assertEquals(sessionsBefore,sessions.count());
        assertTrue(identities.findByUserUserId(local.getUserId()).isEmpty());
        User current=users.findById(local.getUserId()).orElseThrow();assertEquals(Role.TEACHER,current.getRole());assertEquals(37,current.getXp());
        assertEquals(AuthProvider.LOCAL,current.getAuthProvider());
    }
    @ParameterizedTest @ValueSource(strings={"false","missing","string"})
    void providerEmailVerificationMustBeABooleanTrueBeforeAnyAccountWrite(String proof) {
        String email=email();var attrs=attributes("google",subject(),email);
        if(proof.equals("missing"))attrs.remove("email_verified");else attrs.put("email_verified",proof.equals("string")?"true":false);
        response.set(attrs);long count=users.count();
        var rejected=assertThrows(OAuth2AuthenticationException.class,()->oauth.loadUser(request("google")));
        assertEquals("unverified_email",rejected.getError().getErrorCode());assertEquals(count,users.count());
    }
    @Test void exactLegacyIdentityMigratesWithoutChangingTheStoredEmailNameRoleOrXp() {
        String subject=subject();User legacy=account(AuthProvider.GOOGLE,subject,email());
        var principal=fetch("google",subject,email());assertEquals(legacy.getUserId(),principal.userId());
        User current=users.findById(legacy.getUserId()).orElseThrow();assertEquals(legacy.getEmail(),current.getEmail());
        assertEquals(legacy.getName(),current.getName());assertEquals(Role.TEACHER,current.getRole());assertEquals(37,current.getXp());
        assertEquals(1,identities.findByUserUserId(current.getUserId()).size());
        var login=federated.issueSession(principal);assertEquals(legacy.getEmail(),jwt.extractUsername(login.accessToken()));
    }
    @Test void anEnrolledSubjectStillResolvesItsAccountWhenTheProviderEmailMatchesSomebodyElse() {
        String subject=subject();var first=fetch("google",subject,email());User other=account(AuthProvider.LOCAL,null,email());long count=users.count();
        var second=fetch("google",subject,other.getEmail());assertEquals(first.userId(),second.userId());assertEquals(count,users.count());
        assertNotEquals(other.getUserId(),second.userId());
    }
    @ParameterizedTest @ValueSource(strings={"google","discord"})
    void anEmailCollisionCannotReplaceADifferentLegacySubjectOrProvider(String provider) {
        String email=email();User legacy=account(AuthProvider.GOOGLE,subject(),email);
        assertThrows(OAuth2AuthenticationException.class,()->fetch(provider,subject(),email));
        assertTrue(identities.findByUserUserId(legacy.getUserId()).isEmpty());
        assertEquals(legacy.getProviderId(),users.findById(legacy.getUserId()).orElseThrow().getProviderId());
    }
    @Test void ambiguousLegacyProviderSubjectRequiresManualResolutionInsteadOfGuessingByEmail() {
        String subject=subject();User first=account(AuthProvider.GOOGLE,subject,email());account(AuthProvider.GOOGLE,subject,email());
        var rejected=assertThrows(OAuth2AuthenticationException.class,()->fetch("google",subject,first.getEmail()));
        assertEquals("identity_unavailable",rejected.getError().getErrorCode());
        assertTrue(identities.findByUserUserId(first.getUserId()).isEmpty());
    }
    void awaitTwoBlockedEnrollments() {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            Number count=(Number)em.createNativeQuery("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and cardinality(pg_blocking_pids(pid))>0").getSingleResult();
            if(count.intValue()>=2)return;
            try{Thread.sleep(10);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}
        }
        fail("Both enrollments must reach the shared provider identity lock");
    }
    @Test void concurrentEnrollmentOfOneProviderSubjectCreatesOneAccountAndOneIdentity() throws Exception {
        String subject=subject(),email=email();OAuthClaims claims=new OAuthClaims(AuthProvider.GOOGLE,subject,email,"Student",null);
        ExecutorService pool=Executors.newFixedThreadPool(2);List<Future<FederatedIdentityService.Binding>> attempts=new ArrayList<>();
        try {
            tx.executeWithoutResult(status->{
                identities.lockIdentity("oauth:GOOGLE:"+subject);
                attempts.add(pool.submit(()->federated.bind(claims)));attempts.add(pool.submit(()->federated.bind(claims)));
                awaitTwoBlockedEnrollments();
            });
            var first=attempts.get(0).get(15,TimeUnit.SECONDS);var second=attempts.get(1).get(15,TimeUnit.SECONDS);
            assertEquals(first.userId(),second.userId());assertEquals(1,identities.findByUserUserId(first.userId()).size());
            assertEquals(first.userId(),users.findByEmail(email).orElseThrow().getUserId());
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void theDatabaseIndependentlyRejectsDuplicateProviderSubjects() {
        String subject=subject();var principal=fetch("google",subject,email());User other=account(AuthProvider.LOCAL,null,email());
        assertThrows(DataIntegrityViolationException.class,()->tx.executeWithoutResult(status->{
            OAuthIdentity duplicate=new OAuthIdentity();duplicate.setProvider(AuthProvider.GOOGLE);duplicate.setSubject(subject);duplicate.setUser(other);
            identities.saveAndFlush(duplicate);
        }));
        assertEquals(principal.userId(),identities.findUserId(AuthProvider.GOOGLE,subject).orElseThrow());
    }
    @Test void removedIdentityOrInactiveAccountCannotMintCredentialsFromAnEarlierPrincipal() {
        var principal=fetch("google",subject(),email());long count=sessions.count();
        tx.executeWithoutResult(status->identities.deleteByUserUserId(principal.userId()));
        assertThrows(OAuth2AuthenticationException.class,()->federated.issueSession(principal));
        assertEquals(count,sessions.count());
        var another=fetch("google",subject(),email());
        tx.executeWithoutResult(status->users.findForSessionUpdate(another.userId()).orElseThrow().setActive(false));
        assertThrows(OAuth2AuthenticationException.class,()->federated.issueSession(another));
        assertEquals(count,sessions.count());
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test void providerUserinfoIsFetchedOutsideTheDatabaseTransaction() {
        RestTemplate client=mock(RestTemplate.class);String subject=subject();Map<String,Object> attrs=attributes("google",subject,email());
        when(client.exchange(any(RequestEntity.class),any(org.springframework.core.ParameterizedTypeReference.class))).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());return ResponseEntity.ok(attrs);
        });
        oauth.setRestOperations(client);var principal=(FederatedOAuth2User)oauth.loadUser(request("google"));
        assertTrue(users.findById(principal.userId()).isPresent());
    }
    @Test void failedOuterTransactionCannotLeaveAPartialAccountOrIdentity() {
        String subject=subject(),email=email();
        tx.executeWithoutResult(status->{federated.bind(new OAuthClaims(AuthProvider.GOOGLE,subject,email,"Student",null));status.setRollbackOnly();});
        assertTrue(users.findByEmail(email).isEmpty());assertTrue(identities.findUserId(AuthProvider.GOOGLE,subject).isEmpty());
    }
}
