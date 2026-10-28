package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.Course;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.repository.RefreshTokenRepository;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.security.*;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Synthetic accounts and an explicitly opted-in disposable PostgreSQL database only. */
@EnabledIfEnvironmentVariable(named="SECURITY_FOUNDATIONS_TEST_DATABASE_URL", matches="jdbc:postgresql://[^/]+/languageapp_security_fix_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${SECURITY_FOUNDATIONS_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.show-sql=false", "app.frontend.url=https://localhost",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class BrowserOriginPersistenceTest {
    @Autowired WebApplicationContext context;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PasswordEncoder encoder;
    @Autowired RefreshTokenService sessions;
    @Autowired RefreshTokenRepository rows;
    @Autowired JwtService jwt;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    private User user() {
        return new TransactionTemplate(manager).execute(tx -> {
            String key=UUID.randomUUID().toString().substring(0,8);
            var course=new Course();course.setLanguageCode(key);course.setLanguageName("Origin test");em.persist(course);
            var user=new User();user.setName("Origin "+key);user.setEmail(key+"@example.test");user.setVerified(true);
            user.setActiveCourse(course);user.setPasswordHash(encoder.encode("Origin-test-password"));em.persist(user);em.flush();return user;
        });
    }
    private String loginBody(User user) { return "{\"email\":\""+user.getEmail()+"\",\"password\":\"Origin-test-password\"}"; }

    @Test void rejectedLoginAndRegistrationCannotIssueCookiesOrPersistAnAccount() throws Exception {
        User user=user();long before=rows.count();
        for (String origin : new String[]{null,"null","https://evil.example.test","http://localhost","https://localhost:444"}) {
            var request=post("/api/auth/login").contentType("application/json").content(loginBody(user));
            if(origin!=null)request.header("Origin",origin);
            mvc.perform(request).andExpect(status().isForbidden()).andExpect(header().doesNotExist("Set-Cookie"));
        }
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Set-Cookie"));
        assertEquals(before,rows.count());verifyNoInteractions(mail);
    }

    @Test void missingOrSameSiteWrongOriginCannotRefreshOrLogOutARealSession() throws Exception {
        User user=user();var session=sessions.createSession(user.getUserId());
        for(String endpoint : new String[]{"refresh","logout"}) {
            for(String origin : new String[]{null,"https://localhost:5173","https://sub.localhost","null"}) {
                var request=post("/api/auth/"+endpoint).cookie(new MockCookie("refreshToken",session.rawToken()))
                        .header("Sec-Fetch-Site","same-site");
                if(origin!=null)request.header("Origin",origin);
                mvc.perform(request).andExpect(status().isForbidden()).andExpect(header().doesNotExist("Set-Cookie"));
                assertTrue(sessions.isSessionActive(session.id(),user.getEmail()));
            }
        }
        mvc.perform(post("/api/auth/refresh").header("Origin","https://localhost")
                .cookie(new MockCookie("refreshToken",session.rawToken()))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Origin","https://localhost").header("Sec-Fetch-Site","same-origin")
                .cookie(new MockCookie("refreshToken",session.rawToken())))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("refreshToken",0));
        assertFalse(sessions.isSessionActive(session.id(),user.getEmail()));
    }

    @Test void exactConfiguredOriginAllowsLocalLoginAndCorsPreflight() throws Exception {
        User user=user();
        mvc.perform(options("/api/auth/login").header("Origin","https://localhost")
                .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://localhost"));
        mvc.perform(post("/api/auth/login").header("Origin","https://localhost").header("Sec-Fetch-Site","same-origin")
                .contentType("application/json").content(loginBody(user)))
                .andExpect(status().isOk()).andExpect(cookie().httpOnly("refreshToken",true)).andExpect(cookie().secure("refreshToken",true));
    }

    @Test void hostilePreflightIsNotAllowedAndMetadataCannotOverrideOriginPolicy() throws Exception {
        mvc.perform(options("/api/auth/logout").header("Origin","https://evil.example.test")
                .header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").header("Origin","https://localhost").header("Sec-Fetch-Site","cross-site"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(post("/api/auth/logout").header("Origin","https://localhost","https://localhost"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test void bearerOnlyAuthenticationStillRequiresABearerAndNoBrowserOrigin() throws Exception {
        User user=user();var session=sessions.createSession(user.getUserId());
        var details=org.springframework.security.core.userdetails.User.withUsername(user.getEmail()).password("unused").roles("STUDENT").build();
        String access=jwt.generateSessionToken(details,session.id());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+access)).andExpect(status().isOk());
        mvc.perform(get("/api/users/me").cookie(new MockCookie("refreshToken",session.rawToken()))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/oauth-links/cancel").header("Authorization","Bearer "+access)
                .cookie(new MockCookie("refreshToken",session.rawToken())))
                .andExpect(status().isForbidden());
        assertTrue(sessions.isSessionActive(session.id(),user.getEmail()));verifyNoInteractions(mail);
    }

    @Test void oauthProviderRedirectsStillUseStateInsteadOfTheCookieApiOriginGate() throws Exception {
        for(String provider : new String[]{"google","discord"}) {
            mvc.perform(get("/oauth2/authorization/"+provider)).andExpect(status().is3xxRedirection());
            mvc.perform(get("/login/oauth2/code/"+provider).param("code","not-a-real-provider-code").param("state","wrong"))
                    .andExpect(status().is3xxRedirection()).andExpect(header().doesNotExist("Set-Cookie"));
        }
        verifyNoInteractions(mail);
    }
}
