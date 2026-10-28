package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.dto.request.RegisterRequest;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.scheduler.*;
import com.languageapp.backend.security.AuthenticationService;
import com.languageapp.backend.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in only: never connects to or creates a schema in the developer's application database. */
@EnabledIfEnvironmentVariable(named="VERIFICATION_TEST_DATABASE_URL", matches="jdbc:postgresql://[^/]+/languageapp_verification_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties={"spring.datasource.url=${VERIFICATION_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class VerificationLifecyclePersistenceTest {
    @Autowired AuthenticationService auth;
    @Autowired UserRepository users;
    @Autowired VerificationTokenRepository verification;
    @Autowired PasswordResetTokenRepository resets;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired Clock clock;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean SseService sse;
    @MockitoBean ImageStorageService images;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;
    TransactionTemplate tx;
    record Fixture(UUID userId, String email, String token) { }
    @BeforeEach void setup() { tx = new TransactionTemplate(manager); }

    Fixture fixture(long expirySeconds) {
        return tx.execute(status -> {
            String key = UUID.randomUUID().toString().substring(0,8);
            Course course = new Course(); course.setLanguageCode(key); course.setLanguageName("Verification test"); em.persist(course);
            User user = new User(); user.setEmail(key+"@example.test"); user.setName(key); user.setVerified(false);
            user.setUserTag("1234"); user.setFriendCode(key); user.setActiveCourse(course); user.setXp(0); em.persist(user);
            VerificationToken token = new VerificationToken(); token.setUser(user); token.setToken(UUID.randomUUID().toString());
            token.setExpiryDate(clock.instant().plusSeconds(expirySeconds)); em.persist(token); em.flush();
            return new Fixture(user.getUserId(), user.getEmail(), token.getToken());
        });
    }
    RegisterRequest registration() {
        String key = UUID.randomUUID().toString().substring(0,8);
        tx.executeWithoutResult(status -> {
            Course course = new Course(); course.setLanguageCode(key); course.setLanguageName("Signup test"); em.persist(course);
        });
        RegisterRequest request = new RegisterRequest(); request.setName("New student"); request.setEmail(key+"@example.test");
        request.setPassword("Test-password-only"); request.setCourseCode(key); request.setAcceptTerms(true);
        return request;
    }
    void awaitTwoBlockedConsumers() {
        long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime()<deadline) {
            // Refresh the PostgreSQL statistics snapshot to observe newly opened worker connections.
            em.createNativeQuery("select 1 from pg_stat_clear_snapshot()").getSingleResult();
            Number blocked = (Number) em.createNativeQuery("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and cardinality(pg_blocking_pids(pid))>0").getSingleResult();
            if (blocked.intValue()>=2) return;
            try { Thread.sleep(10); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        }
        fail("Both consumers must demonstrably wait for the account lock before the competing update commits");
    }
    @Test void concurrentVerificationConsumesOnceAndCannotOverwriteAnAccountUpdate() throws Exception {
        Fixture f = fixture(300);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> attempts = new ArrayList<>();
        try {
            tx.executeWithoutResult(status -> {
                User locked = users.findForSessionUpdate(f.userId()).orElseThrow();
                Callable<Boolean> consume = () -> {
                    try { auth.verifyEmail(f.token()); return true; }
                    catch (BadRequestException consumed) { return false; }
                };
                attempts.add(workers.submit(consume)); attempts.add(workers.submit(consume));
                awaitTwoBlockedConsumers();
                locked.setXp(50);
            });
            int accepted = 0;
            for (Future<Boolean> result : attempts) if (result.get(15,TimeUnit.SECONDS)) accepted++;
            assertEquals(1, accepted);
            User current = users.findById(f.userId()).orElseThrow();
            assertTrue(current.isVerified()); assertEquals(50,current.getXp());
            assertTrue(verification.findByToken(f.token()).isEmpty());
            assertThrows(BadRequestException.class, () -> auth.verifyEmail(f.token()));
        } finally { workers.shutdownNow(); assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS)); }
    }
    @Test void disabledAccountsAndExpiredLinksCannotBeActivated() {
        Fixture disabled = fixture(300), expired = fixture(-60);
        tx.executeWithoutResult(status -> users.findForSessionUpdate(disabled.userId()).orElseThrow().setActive(false));
        assertThrows(BadRequestException.class, () -> auth.verifyEmail(disabled.token()));
        assertThrows(BadRequestException.class, () -> auth.verifyEmail(expired.token()));
        assertFalse(users.findById(disabled.userId()).orElseThrow().isVerified());
        assertFalse(users.findById(expired.userId()).orElseThrow().isVerified());
    }
    @Test void rollbackPreservesTheUnconsumedVerificationLink() {
        Fixture f = fixture(300);
        tx.executeWithoutResult(status -> { auth.verifyEmail(f.token()); status.setRollbackOnly(); });
        assertFalse(users.findById(f.userId()).orElseThrow().isVerified());
        assertTrue(verification.findByToken(f.token()).isPresent());
        auth.verifyEmail(f.token());
        assertTrue(users.findById(f.userId()).orElseThrow().isVerified());
    }
    @Test void signupMailIsDispatchedOnlyAfterItsAccountAndTokenCommit() {
        RegisterRequest request = registration();
        tx.executeWithoutResult(status -> {
            auth.register(request);
            verifyNoInteractions(mail);
        });
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mail).sendVerificationEmail(eq(request.getEmail()),eq(request.getName()),token.capture());
        assertTrue(users.findByEmail(request.getEmail()).isPresent());
        assertTrue(verification.findByToken(token.getValue()).isPresent());
    }
    @Test void rolledBackSignupCannotSendADeadLink() {
        RegisterRequest request = registration();
        tx.executeWithoutResult(status -> { auth.register(request); verifyNoInteractions(mail); status.setRollbackOnly(); });
        verifyNoInteractions(mail);
        assertTrue(users.findByEmail(request.getEmail()).isEmpty());
    }
    @Test void rejectedMailSchedulingCannotTurnCommittedSignupIntoAnError() {
        RegisterRequest request = registration();
        doThrow(new org.springframework.core.task.TaskRejectedException("test-only queue saturation"))
                .when(mail).sendVerificationEmail(anyString(),anyString(),anyString());
        assertDoesNotThrow(() -> auth.register(request));
        assertTrue(users.findByEmail(request.getEmail()).isPresent());
    }
    @Test void resetMailWaitsForCommitAndUsesThePersistedToken() {
        Fixture f = fixture(300);
        tx.executeWithoutResult(status -> { auth.requestPasswordReset(f.email()); verifyNoInteractions(mail); });
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordResetEmail(eq(f.email()),token.capture());
        assertTrue(resets.findByToken(token.getValue()).isPresent());
    }
    @Test void rolledBackResetRequestCannotDispatchMail() {
        Fixture f = fixture(300);
        tx.executeWithoutResult(status -> { auth.requestPasswordReset(f.email()); verifyNoInteractions(mail); status.setRollbackOnly(); });
        verifyNoInteractions(mail);
    }
    @Test void unregisteredResetRecipientRemainsNonEnumerableAndDoesNotDispatchMail() {
        assertDoesNotThrow(() -> auth.requestPasswordReset("unknown-"+UUID.randomUUID()+"@example.test"));
        verifyNoInteractions(mail);
    }
    @Test void inactiveAccountCannotRequestOrConsumeAPasswordReset() {
        Fixture f=fixture(300);
        String token=UUID.randomUUID().toString();
        tx.executeWithoutResult(s->{
            var user=users.findById(f.userId()).orElseThrow();user.setActive(false);
            var reset=new PasswordResetToken();reset.setUser(user);reset.setToken(token);reset.setExpiryDate(clock.instant().plusSeconds(300));resets.saveAndFlush(reset);
        });
        assertDoesNotThrow(()->auth.requestPasswordReset(f.email()));verifyNoInteractions(mail);
        assertTrue(resets.findByToken(token).isPresent());
        assertThrows(BadRequestException.class,()->auth.resetPassword(token,"New-valid-password-2026"));
        assertFalse(users.findById(f.userId()).orElseThrow().isActive());assertTrue(resets.findByToken(token).isPresent());
    }
}
