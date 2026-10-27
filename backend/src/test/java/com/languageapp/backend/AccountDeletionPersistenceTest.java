package com.languageapp.backend;

import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.AuthProvider;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.AccountDeletionTokenRepository;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.security.RefreshTokenService;
import com.languageapp.backend.service.AccountDeletionService;
import com.languageapp.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Schema creation is allowed only in an explicitly named, disposable PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "ACCOUNT_DELETION_TEST_DATABASE_URL", matches = "jdbc:postgresql://[^/]+/languageapp_deletion_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties = {"spring.datasource.url=${ACCOUNT_DELETION_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="})
class AccountDeletionPersistenceTest {
    @Autowired AccountDeletionService service;
    @Autowired UserRepository users;
    @Autowired AccountDeletionTokenRepository tokens;
    @Autowired com.languageapp.backend.repository.OAuthIdentityRepository identities;
    @Autowired RefreshTokenService sessions;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean EmailService emailService;
    @MockitoBean DataSeeder dataSeeder;
    @MockitoBean com.languageapp.backend.service.ImageStorageService images;
    @MockitoBean com.languageapp.backend.service.SseService sse;
    @MockitoBean com.languageapp.backend.scheduler.AssignmentExpiryScheduler expiry;
    @MockitoBean com.languageapp.backend.scheduler.ChallengeScheduler challenges;
    @MockitoBean Clock clock;
    TransactionTemplate tx;
    record Fixture(UUID id, String email, String session, String secret) {}

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-24T13:00:00Z"));
    }

    Fixture issue() {
        User user = tx.execute(status -> {
            User u = new User(); u.setEmail(UUID.randomUUID() + "@example.test"); u.setName("Deletion persistence test");
            u.setAuthProvider(AuthProvider.DISCORD); u.setProviderId(UUID.randomUUID().toString());
            users.saveAndFlush(u);
            var identity=new com.languageapp.backend.entity.OAuthIdentity();identity.setUser(u);identity.setProvider(AuthProvider.DISCORD);identity.setSubject(u.getProviderId());
            identities.saveAndFlush(identity);
            return u;
        });
        String session = sessions.createRefreshToken(user.getUserId());
        service.request(user.getEmail(), session);
        ArgumentCaptor<String> secret = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAccountDeletionConfirmation(eq(user.getEmail()), secret.capture());
        return new Fixture(user.getUserId(), user.getEmail(), session, secret.getValue());
    }

    @Test void mappingAndPreviewDoNotConsumeApprovalOrModifyAccount() {
        Fixture f = issue();
        service.preview(f.email(), f.session(), f.secret());
        service.preview(f.email(), f.session(), f.secret());
        assertEquals(64, tokens.findById(f.id()).orElseThrow().getTokenHash().length());
        assertEquals(f.email(), users.findById(f.id()).orElseThrow().getEmail());
        assertTrue(sessions.findByRawToken(f.session()).isPresent());
    }

    @Test void twoSimultaneousConfirmationsHaveExactlyOneWinner() throws Exception {
        Fixture f = issue();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> confirm = () -> {
            start.await();
            try { service.confirm(f.email(), f.session(), f.secret(), true); return true; }
            catch (BadRequestException expected) { return false; }
        };
        try {
            Future<Boolean> first = pool.submit(confirm), second = pool.submit(confirm);
            start.countDown();
            int winners = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, winners);
        } finally { pool.shutdownNow(); }
        assertFalse(users.findById(f.id()).orElseThrow().isActive());
        assertTrue(tokens.findById(f.id()).isEmpty());
        assertTrue(sessions.findByRawToken(f.session()).isEmpty());
        assertTrue(identities.findByUserUserId(f.id()).isEmpty());
    }

    @Test void failedOuterTransactionRollsBackDeletionConsumptionAndSessionRevocationTogether() {
        Fixture f = issue();
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            service.confirm(f.email(), f.session(), f.secret(), true);
            throw new IllegalStateException("Deliberate rollback in isolated test database");
        }));
        assertTrue(users.findById(f.id()).orElseThrow().isActive());
        assertTrue(sessions.findByRawToken(f.session()).isPresent());
        assertEquals(f.email(), service.preview(f.email(), f.session(), f.secret()).email());
        assertEquals(1,identities.findByUserUserId(f.id()).size());
    }
}
