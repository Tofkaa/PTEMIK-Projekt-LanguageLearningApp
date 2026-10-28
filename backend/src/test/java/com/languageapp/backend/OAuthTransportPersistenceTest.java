package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.controller.OAuthLoginController;
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
import org.springframework.mock.web.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="OAUTH_TRANSPORT_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_transport_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${OAUTH_TRANSPORT_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.show-sql=false","app.frontend.url=https://localhost",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class OAuthTransportPersistenceTest {
    @Autowired WebApplicationContext context;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired CourseRepository courses;
    @Autowired RefreshTokenRepository sessionRows;
    @Autowired RefreshTokenService sessions;
    @Autowired FederatedIdentityService identities;
    @Autowired OAuth2AuthenticationSuccessHandler success;
    @Autowired OAuthLoginController exchange;
    @Autowired JwtService jwt;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    MockMvc mvc;TransactionTemplate tx;
    record Handoff(User user,MockHttpServletRequest request,String code) { }
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    Handoff handoff(String provider)throws Exception {
        User user=tx.execute(s->{
            var course=courses.findByLanguageCode("en").orElseGet(()->{var c=new Course();c.setLanguageCode("en");c.setLanguageName("English");em.persist(c);return c;});
            var u=new User();u.setEmail(UUID.randomUUID()+"@example.test");u.setName("Chosen profile");u.setActiveCourse(course);u.setVerified(true);
            u.setAuthProvider(AuthProvider.valueOf(provider.toUpperCase()));u.setProviderId("subject-"+UUID.randomUUID());em.persist(u);em.flush();return u;
        });
        var binding=identities.bind(new OAuthClaims(user.getAuthProvider(),user.getProviderId(),user.getEmail(),user.getName(),null));
        var principal=new FederatedOAuth2User(binding,new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),Map.of("sub",user.getProviderId()),"sub"));
        var browser=new MockHttpServletRequest();var result=new MockHttpServletResponse();
        success.onAuthenticationSuccess(browser,result,new OAuth2AuthenticationToken(principal,principal.getAuthorities(),provider));
        String location=result.getRedirectedUrl();assertTrue(location.startsWith("https://localhost/oauth2/redirect#code="));
        assertFalse(location.contains("?"));assertNull(result.getHeader("Set-Cookie"));assertEquals("no-store",result.getHeader("Cache-Control"));
        assertEquals(0,sessionRows.findAll().stream().filter(row->row.getUser().getUserId().equals(user.getUserId())).count());
        return new Handoff(user,browser,location.split("#code=")[1]);
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postCode(Handoff value) {
        var builder=post("/api/auth/oauth/exchange").header("Origin","https://localhost")
                .contentType("application/json").content("{\"code\":\""+value.code+"\"}");
        if(value.request.getSession(false) instanceof MockHttpSession browser)builder.session(browser);
        return builder;
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"google","discord"})
    void onlyTheHttpsPostExchangeReturnsCredentialsForBothProviders(String provider)throws Exception {
        var value=handoff(provider);var browser=(MockHttpSession)value.request.getSession(false);
        var response=mvc.perform(postCode(value)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(cookie().httpOnly("refreshToken",true)).andExpect(cookie().secure("refreshToken",true)).andReturn().getResponse();
        String access=com.jayway.jsonpath.JsonPath.read(response.getContentAsString(),"$.accessToken");assertEquals(value.user.getEmail(),jwt.extractUsername(access));
        assertTrue(browser.isInvalid());assertEquals(0,response.getCookie("JSESSIONID").getMaxAge());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+access)).andExpect(status().isOk());
        mvc.perform(postCode(value)).andExpect(status().isBadRequest());
    }
    @Test void wrongBrowserAndWrongProofCannotUseTheOriginalPendingLogin()throws Exception {
        var value=handoff("google");
        mvc.perform(post("/api/auth/oauth/exchange").header("Origin","https://localhost").contentType("application/json").content("{\"code\":\""+value.code+"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(postCode(value).content("{\"code\":\""+"x".repeat(43)+"\"}")).andExpect(status().isBadRequest());
        mvc.perform(postCode(value)).andExpect(status().isOk());
    }
    @Test void missingOrHostileOriginAndNonJsonBodiesCannotConsumeTheHandoff()throws Exception {
        var value=handoff("google");
        mvc.perform(post("/api/auth/oauth/exchange").session((MockHttpSession)value.request.getSession(false)).contentType("application/json").content("{\"code\":\""+value.code+"\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(postCode(value).with(request->{request.removeHeader("Origin");request.addHeader("Origin","https://evil.example.test");return request;})).andExpect(status().isForbidden());
        mvc.perform(postCode(value).contentType("text/plain")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(postCode(value)).andExpect(status().isOk());
    }
    @Test void accountRevokedBetweenProviderVerificationAndExchangeCannotReceiveASession()throws Exception {
        var value=handoff("google");tx.executeWithoutResult(s->users.findForSessionUpdate(value.user.getUserId()).orElseThrow().setActive(false));
        mvc.perform(postCode(value)).andExpect(status().isBadRequest());
        assertEquals(0,sessionRows.findAll().stream().filter(row->row.getUser().getUserId().equals(value.user.getUserId())).count());
    }
    @Test void concurrentExchangesIssueExactlyOneApplicationSession()throws Exception {
        var value=handoff("google");var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        Callable<Boolean> redeem=()->{go.await();try{exchange.exchange(new OAuthLoginController.Exchange(value.code),"https://localhost",value.request);return true;}
            catch(com.languageapp.backend.exception.BadRequestException duplicate){return false;}};
        try{var first=pool.submit(redeem);var second=pool.submit(redeem);go.countDown();assertNotEquals(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
        assertEquals(1,sessionRows.findAll().stream().filter(row->row.getUser().getUserId().equals(value.user.getUserId())).count());
    }
    @Test void queryJwtIsRejectedButBearerHeaderOpensOnlyABoundedStream()throws Exception {
        var value=handoff("google");var login=exchange.exchange(new OAuthLoginController.Exchange(value.code),"https://localhost",value.request);
        String token=login.getBody().accessToken();
        mvc.perform(get("/api/notifications/stream").param("token",token)).andExpect(status().isUnauthorized());verifyNoInteractions(sse);
        var opened=new java.util.concurrent.atomic.AtomicReference<SseEmitter>();
        when(sse.subscribe(eq(value.user.getEmail()),anyLong())).thenAnswer(call->{long ttl=call.getArgument(1);assertTrue(ttl>0 && ttl<=300000);var emitter=new SseEmitter(ttl);opened.set(emitter);return emitter;});
        var stream=mvc.perform(get("/api/notifications/stream").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
        opened.get().complete();
        mvc.perform(asyncDispatch(stream)).andExpect(status().isOk());
        verify(sse).subscribe(eq(value.user.getEmail()),anyLong());
    }
}
