package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.*;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.security.*;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="OBSERVABILITY_TEST_DATABASE_URL",matches="jdbc:postgresql://[^/]+/languageapp_observability_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${OBSERVABILITY_TEST_DATABASE_URL}","spring.jpa.hibernate.ddl-auto=create","spring.jpa.show-sql=false","management.endpoints.web.exposure.include=health,metrics","management.endpoint.health.show-details=never","management.health.mail.enabled=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
@org.springframework.context.annotation.Import(ObservabilityPersistenceTest.Probes.class)
class ObservabilityPersistenceTest {
    @Autowired com.languageapp.backend.observability.RequestDiagnosticsFilter diagnostics;
    @Autowired com.languageapp.backend.observability.DatabaseWaitProbe databaseProbe;
    @Autowired javax.sql.DataSource datasource;

    @Autowired AdminService admins;
    @Autowired UserService profiles;
    @Autowired AccountDeletionService deletion;
    @Autowired AuthenticationService authentication;
    @Autowired CustomUserDetailsService details;
    @Autowired RefreshTokenService refresh;
    @Autowired JwtService jwt;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired WebApplicationContext context;
    @Autowired PasswordEncoder encoder;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;MockMvc mvc;
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(diagnostics).apply(springSecurity()).build();}
    User user(Role role,boolean verified){return tx.execute(status->{
        String id=UUID.randomUUID().toString().substring(0,8);var c=new Course();c.setLanguageCode(id);c.setLanguageName("Test");em.persist(c);
        var u=new User();u.setName(id);u.setEmail(id+"@example.test");u.setRole(role);u.setVerified(verified);u.setActiveCourse(c);u.setPasswordHash(encoder.encode("Policy-test-password"));em.persist(u);em.flush();return u;
    });}
    String auth(User u){var s=refresh.createSession(u.getUserId());return "Bearer "+jwt.generateSessionToken(org.springframework.security.core.userdetails.User.withUsername(u.getEmail()).password("test-only").authorities("ROLE_"+u.getRole().name()).build(),s.id());}

    @org.springframework.web.bind.annotation.RestController
    static class Probes {
        @org.springframework.web.bind.annotation.GetMapping("/api/r28-probes/status/{status}")
        void status(@org.springframework.web.bind.annotation.PathVariable int status){switch(status){
            case 400 -> throw new com.languageapp.backend.exception.BadRequestException("Expected invalid input");
            case 403 -> throw new com.languageapp.backend.exception.ForbiddenException("Expected forbidden");
            case 404 -> throw new com.languageapp.backend.exception.ResourceNotFoundException("Expected missing");
            case 409 -> throw new com.languageapp.backend.exception.ConflictException("Expected changed state");
            case 413 -> throw new com.languageapp.backend.exception.UploadTooLargeException();
            case 429 -> throw new com.languageapp.backend.exception.TooManyRequestsException(7);
            case 503 -> throw new com.languageapp.backend.exception.TranslationUnavailableException();
            default -> throw new IllegalStateException("PRIVATE_EXCEPTION_VALUE",new IllegalArgumentException("PRIVATE_CAUSE_VALUE"));
        }}
        @org.springframework.web.bind.annotation.GetMapping("/api/r28-probes/persistence/{kind}")
        void persistence(@org.springframework.web.bind.annotation.PathVariable String kind){switch(kind){
            case "constraint" -> throw new org.springframework.dao.DataIntegrityViolationException("PRIVATE_SQL_BIND_VALUE");
            case "optimistic" -> throw new org.springframework.orm.ObjectOptimisticLockingFailureException(User.class,"PRIVATE_ACTOR");
            case "lock" -> throw new org.springframework.dao.CannotAcquireLockException("PRIVATE_LOCK_DETAIL");
            default -> throw new org.springframework.dao.QueryTimeoutException("PRIVATE_QUERY_DETAIL");
        }}
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={400,403,404,409,413,429,500,503})
    void publicErrorContractCorrelatesEveryStatusWithoutLeakingExceptionData(int status)throws Exception {
        User u=user(Role.STUDENT,true);
        var result=mvc.perform(get("/api/r28-probes/status/"+status).queryParam("token","PRIVATE_QUERY_VALUE").header("Authorization",auth(u)))
            .andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status)).andExpect(jsonPath("$.timestamp").exists()).andReturn();
        var response=result.getResponse();String id=response.getHeader("X-Request-ID");
        assertEquals(id,com.languageapp.backend.observability.RequestDiagnostics.valid(id));
        assertEquals(id,new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).get("requestId").asText());
        assertFalse(response.getContentAsString().contains("PRIVATE"));
        if(status==429)assertEquals("7",response.getHeader("Retry-After"));
    }
    @ParameterizedTest @ValueSource(strings={"constraint","optimistic","lock","query"})
    void persistenceFailuresKeepRetrySemanticsAndNeverReturnSql(String kind)throws Exception {
        var result=mvc.perform(get("/api/r28-probes/persistence/"+kind).header("Authorization",auth(user(Role.STUDENT,true))))
            .andExpect(status().is(kind.equals("query")?503:409)).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("PRIVATE"));
        assertNotNull(result.getResponse().getHeader("X-Request-ID"));
    }
    @Test void actuatorIsProtectedByCurrentVerifiedAdministratorAuthority()throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.requestId").exists());
        for(Role role:List.of(Role.STUDENT,Role.TEACHER))mvc.perform(get("/actuator/metrics").header("Authorization",auth(user(role,true))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.requestId").exists());
        mvc.perform(get("/actuator/metrics").header("Authorization",auth(user(Role.ADMIN,false)))).andExpect(status().isForbidden());
        String admin=auth(user(Role.ADMIN,true));
        mvc.perform(get("/actuator/metrics").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.names").isArray());
        mvc.perform(get("/actuator/metrics/app.mail.queue").header("Authorization",admin)).andExpect(status().isOk());
        mvc.perform(get("/actuator/health").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.details").doesNotExist());
        for(String hidden:List.of("env","configprops","loggers","heapdump"))mvc.perform(get("/actuator/"+hidden).header("Authorization",admin)).andExpect(status().isNotFound());
    }
    @Test void earlySecurityFailuresAlsoReceiveServerGeneratedCorrelation()throws Exception {
        var result=mvc.perform(get("/api/users/me").header("Authorization","Bearer PRIVATE_INVALID_TOKEN").header("X-Request-ID","PRIVATE_FORGED_ID"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.requestId").exists()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("PRIVATE"));
        assertEquals(result.getResponse().getHeader("X-Request-ID"),com.languageapp.backend.observability.RequestDiagnostics.valid(result.getResponse().getHeader("X-Request-ID")));
    }
    @Test void databaseProbeObservesARealWaitingLockWithoutReturningSqlOrIdentities()throws Exception {
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(datasource);
        jdbc.execute("create table observability_lock_probe(id int primary key,value int not null)");jdbc.update("insert into observability_lock_probe values(1,0)");
        var held=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var started=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var owner=pool.submit(()->{try(var connection=datasource.getConnection()){
                connection.setAutoCommit(false);try(var statement=connection.createStatement()){statement.executeUpdate("update observability_lock_probe set value=1 where id=1");held.countDown();release.await(10,java.util.concurrent.TimeUnit.SECONDS);connection.rollback();}
            }catch(Exception failure){throw new RuntimeException(failure);}});
            assertTrue(held.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var waiter=pool.submit(()->{try(var connection=datasource.getConnection()){
                connection.setAutoCommit(false);try(var statement=connection.createStatement()){statement.setQueryTimeout(5);started.countDown();statement.executeUpdate("update observability_lock_probe set value=2 where id=1");connection.rollback();}
            }catch(Exception failure){throw new RuntimeException(failure);}});
            assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));
            boolean observed=false;for(int attempt=0;attempt<100;attempt++){
                var snapshot=databaseProbe.sample();if(snapshot.waiters()>0){assertTrue(snapshot.oldestSeconds()>=0);observed=true;break;}Thread.sleep(20);
            }
            assertTrue(observed,"Expected a real PostgreSQL Lock wait");release.countDown();owner.get(10,java.util.concurrent.TimeUnit.SECONDS);waiter.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(0,databaseProbe.sample().waiters());
        }finally{release.countDown();pool.shutdownNow();jdbc.execute("drop table observability_lock_probe");}
    }
}
