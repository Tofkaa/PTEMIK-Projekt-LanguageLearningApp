package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Shares only the opt-in disposable database convention with the verification lifecycle tests. */
@EnabledIfEnvironmentVariable(named="VERIFICATION_TEST_DATABASE_URL", matches="jdbc:postgresql://[^/]+/languageapp_verification_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${VERIFICATION_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class EmailChangeLifecyclePersistenceTest {
    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired EmailChangeTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired Clock clock;
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @Autowired com.languageapp.backend.security.JwtService jwt;
    @Autowired com.languageapp.backend.security.RefreshTokenService sessions;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean SseService sse;
    @MockitoBean ImageStorageService images;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;
    private static final String PASSWORD = "Current-test-password";
    record Fixture(UUID id, String email) { }
    @BeforeEach void setup() { tx = new TransactionTemplate(manager); }
    String target() { return "target-"+UUID.randomUUID()+"@example.test"; }
    Fixture fixture() {
        return tx.execute(status -> {
            String key = UUID.randomUUID().toString().substring(0,8);
            Course course = new Course(); course.setLanguageCode(key); course.setLanguageName("Email change test"); em.persist(course);
            User user = new User(); user.setEmail(key+"@example.test"); user.setName(key); user.setVerified(true);
            user.setUserTag("1234"); user.setFriendCode(key); user.setActiveCourse(course); user.setXp(0);
            user.setPasswordHash(encoder.encode(PASSWORD)); em.persist(user); em.flush();
            return new Fixture(user.getUserId(),user.getEmail());
        });
    }
    EmailChangeToken seed(Fixture f, String destination) {
        return tx.execute(status -> {
            EmailChangeToken token = new EmailChangeToken(); token.setUser(users.findById(f.id()).orElseThrow());
            token.setNewEmail(destination); token.setOtpCode("123456"); token.setExpiryDate(clock.instant().plusSeconds(900));
            em.persist(token); em.flush(); return token;
        });
    }
    long count(Fixture f) {
        return tx.execute(status -> em.createQuery("select count(t) from EmailChangeToken t where t.user.userId=:id",Long.class)
                .setParameter("id",f.id()).getSingleResult());
    }
    void awaitTwoBlockedConsumers() { awaitBlockedOperations(2); }
    void awaitBlockedOperations(int expected) {
        long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime()<deadline) {
            Number blocked = (Number) em.createNativeQuery("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and cardinality(pg_blocking_pids(pid))>0").getSingleResult();
            if (blocked.intValue()>=expected) return;
            try { Thread.sleep(10); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        }
        fail("Both operations must wait for the shared account lock before the competing update commits");
    }
    @Test void parallelRequestsLeaveOneChallengeAndPreserveAnUnrelatedAccountUpdate() throws Exception {
        Fixture f = fixture(); String a = target(), b = target();
        ExecutorService workers = Executors.newFixedThreadPool(2); List<Future<?>> attempts = new ArrayList<>();
        try {
            tx.executeWithoutResult(status -> {
                User locked = users.findForSessionUpdate(f.id()).orElseThrow();
                attempts.add(workers.submit(() -> service.requestEmailChange(f.email(),a,PASSWORD)));
                attempts.add(workers.submit(() -> service.requestEmailChange(f.email(),b,PASSWORD)));
                awaitTwoBlockedConsumers(); locked.setXp(75);
            });
            for (Future<?> attempt : attempts) attempt.get(15,TimeUnit.SECONDS);
            EmailChangeToken current = tokens.findByUser_UserId(f.id()).orElseThrow();
            assertTrue(Set.of(a,b).contains(current.getNewEmail())); assertEquals(1,count(f));
            assertEquals(75,users.findById(f.id()).orElseThrow().getXp());
            verify(mail,times(2)).sendEmailChangeOtp(anyString(),anyString());
            verify(mail,times(2)).sendEmailChangeSecurityAlert(eq(f.email()),anyString());
        } finally { workers.shutdownNow(); assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS)); }
    }
    @Test void parallelConsumptionChangesTheEmailOnceWithoutOverwritingXp() throws Exception {
        Fixture f = fixture(); String destination = target(); seed(f,destination);
        ExecutorService workers = Executors.newFixedThreadPool(2); List<Future<Boolean>> attempts = new ArrayList<>();
        try {
            tx.executeWithoutResult(status -> {
                User locked = users.findForSessionUpdate(f.id()).orElseThrow();
                Callable<Boolean> consume = () -> {
                    try { service.verifyEmailChange(f.email(),"123456"); return true; }
                    catch (BadRequestException | ResourceNotFoundException used) { return false; }
                };
                attempts.add(workers.submit(consume)); attempts.add(workers.submit(consume));
                awaitTwoBlockedConsumers(); locked.setXp(75);
            });
            int accepted = 0; for (Future<Boolean> attempt : attempts) if (attempt.get(15,TimeUnit.SECONDS)) accepted++;
            assertEquals(1,accepted); User current = users.findById(f.id()).orElseThrow();
            assertEquals(destination,current.getEmail()); assertEquals(75,current.getXp()); assertEquals(0,count(f));
        } finally { workers.shutdownNow(); assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS)); }
    }
    @Test void bothEmailsWaitForCommitAndTheOtpMatchesTheStoredChallenge() {
        Fixture f = fixture(); String destination = target();
        tx.executeWithoutResult(status -> { service.requestEmailChange(f.email(),destination,PASSWORD); verifyNoInteractions(mail); });
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mail).sendEmailChangeOtp(eq(destination),code.capture());
        verify(mail).sendEmailChangeSecurityAlert(f.email(),destination);
        assertEquals(code.getValue(),tokens.findByUser_UserId(f.id()).orElseThrow().getOtpCode());
    }
    @Test void rollbackCannotDispatchEitherEmailOrLeaveAChallenge() {
        Fixture f = fixture();
        tx.executeWithoutResult(status -> { service.requestEmailChange(f.email(),target(),PASSWORD); verifyNoInteractions(mail); status.setRollbackOnly(); });
        verifyNoInteractions(mail); assertEquals(0,count(f));
    }
    @Test void rolledBackReissuePreservesTheEarlierChallengeAndSendsNothing() {
        Fixture f = fixture(); EmailChangeToken original = seed(f,target());
        tx.executeWithoutResult(status -> { service.requestEmailChange(f.email(),target(),PASSWORD); status.setRollbackOnly(); });
        verifyNoInteractions(mail); EmailChangeToken current = tokens.findByUser_UserId(f.id()).orElseThrow();
        assertEquals(original.getId(),current.getId()); assertEquals(original.getOtpCode(),current.getOtpCode());
        assertEquals(original.getNewEmail(),current.getNewEmail()); assertEquals(1,count(f));
    }
    @Test void rolledBackConsumptionKeepsTheOldEmailAndTheUsableCode() {
        Fixture f = fixture(); String destination = target(); seed(f,destination);
        tx.executeWithoutResult(status -> { service.verifyEmailChange(f.email(),"123456"); status.setRollbackOnly(); });
        assertEquals(f.email(),users.findById(f.id()).orElseThrow().getEmail()); assertEquals(1,count(f));
        service.verifyEmailChange(f.email(),"123456");
        assertEquals(destination,users.findById(f.id()).orElseThrow().getEmail()); assertEquals(0,count(f));
    }
    @Test void failedOtpSchedulingDoesNotSuppressTheIndependentSecurityAlert() {
        Fixture f = fixture(); String destination = target();
        doThrow(new org.springframework.core.task.TaskRejectedException("test-only queue saturation"))
                .when(mail).sendEmailChangeOtp(anyString(),anyString());
        assertDoesNotThrow(() -> service.requestEmailChange(f.email(),destination,PASSWORD));
        verify(mail).sendEmailChangeSecurityAlert(f.email(),destination);
        assertEquals(destination,tokens.findByUser_UserId(f.id()).orElseThrow().getNewEmail());
    }
    @Test void reissueReplacesThePendingChallengeInsteadOfAddingAnotherRow() {
        Fixture f = fixture(); EmailChangeToken original = seed(f,target()); String destination = target();
        service.requestEmailChange(f.email(),destination,PASSWORD);
        EmailChangeToken replacement = tokens.findByUser_UserId(f.id()).orElseThrow();
        assertNotEquals(original.getId(),replacement.getId()); assertEquals(destination,replacement.getNewEmail());
        assertTrue(tokens.findById(original.getId()).isEmpty()); assertEquals(1,count(f));
    }

    @Test void failedHttpResponsesCommitTheCounterAndEvenTheCorrectCodeIsRefusedAfterExhaustion() throws Exception {
        Fixture f=fixture();seed(f,target());
        var session=sessions.createSession(f.id());
        var details=org.springframework.security.core.userdetails.User.withUsername(f.email()).password("unused").roles("STUDENT").build();
        String authorization="Bearer "+jwt.generateSessionToken(details,session.id());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        for(int failed=1;failed<=5;failed++) {
            var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/users/me/email-change/verify")
                    .header("Authorization",authorization).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"otpCode\":\"000000\"}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest()).andReturn();
            assertEquals(failed,tokens.findByUser_UserId(f.id()).orElseThrow().getFailedAttempts());
            if(failed==5)assertTrue(result.getResponse().getContentAsString().contains("új ellenőrző kódot"));
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/users/me/email-change/verify")
                .header("Authorization",authorization).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"otpCode\":\"123456\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        assertEquals(f.email(),users.findById(f.id()).orElseThrow().getEmail());
        assertEquals(5,tokens.findByUser_UserId(f.id()).orElseThrow().getFailedAttempts());
    }
    @Test void parallelIncorrectCodesCannotExceedOrRollBackThePersistentBudget() throws Exception {
        Fixture f=fixture();seed(f,target());
        ExecutorService workers=Executors.newFixedThreadPool(8);List<Future<?>> attempts=new ArrayList<>();
        try {
            tx.executeWithoutResult(status->{
                users.findForSessionUpdate(f.id()).orElseThrow();
                for(int i=0;i<8;i++)attempts.add(workers.submit(()->{
                    assertThrows(com.languageapp.backend.exception.IncorrectOtpException.class,
                            ()->service.verifyEmailChange(f.email(),"000000"));
                }));
                awaitBlockedOperations(8);
            });
            for(Future<?> attempt:attempts)attempt.get(15,TimeUnit.SECONDS);
            assertEquals(5,tokens.findByUser_UserId(f.id()).orElseThrow().getFailedAttempts());
            assertEquals(f.email(),users.findById(f.id()).orElseThrow().getEmail());
            assertThrows(com.languageapp.backend.exception.IncorrectOtpException.class,()->service.verifyEmailChange(f.email(),"123456"));
        } finally {workers.shutdownNow();assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void reissueAfterExhaustionCreatesAUsableNewChallenge() {
        Fixture f=fixture();seed(f,target());
        for(int i=0;i<5;i++)assertThrows(com.languageapp.backend.exception.IncorrectOtpException.class,()->service.verifyEmailChange(f.email(),"000000"));
        String destination=target();service.requestEmailChange(f.email(),destination,PASSWORD);
        EmailChangeToken current=tokens.findByUser_UserId(f.id()).orElseThrow();assertEquals(0,current.getFailedAttempts());
        service.verifyEmailChange(f.email(),current.getOtpCode());
        assertEquals(destination,users.findById(f.id()).orElseThrow().getEmail());assertEquals(0,count(f));
    }
    @Test void correctCodeWithinTheBudgetStillChangesTheEmail() {
        Fixture f=fixture();String destination=target();seed(f,destination);
        for(int i=0;i<4;i++)assertThrows(com.languageapp.backend.exception.IncorrectOtpException.class,()->service.verifyEmailChange(f.email(),"000000"));
        service.verifyEmailChange(f.email(),"123456");
        assertEquals(destination,users.findById(f.id()).orElseThrow().getEmail());assertEquals(0,count(f));
    }
    @Test void unrelatedBusinessErrorsStillMarkTheTransactionForRollback() {
        Fixture f=fixture(),other=fixture();seed(f,other.email());
        assertThrows(org.springframework.transaction.UnexpectedRollbackException.class,()->tx.executeWithoutResult(status->{
            users.findForSessionUpdate(f.id()).orElseThrow().setXp(99);
            assertThrows(BadRequestException.class,()->service.verifyEmailChange(f.email(),"123456"));
        }));
        assertEquals(0,users.findById(f.id()).orElseThrow().getXp());
        assertEquals(f.email(),users.findById(f.id()).orElseThrow().getEmail());
        assertEquals(0,tokens.findByUser_UserId(f.id()).orElseThrow().getFailedAttempts());
    }
}
