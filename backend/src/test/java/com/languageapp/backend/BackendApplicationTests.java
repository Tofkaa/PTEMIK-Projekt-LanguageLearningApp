package com.languageapp.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.languageapp.backend.config.DataSeeder;
import com.languageapp.backend.scheduler.AssignmentExpiryScheduler;
import com.languageapp.backend.scheduler.ChallengeScheduler;
import com.languageapp.backend.service.EmailService;
import com.languageapp.backend.service.ImageStorageService;
import com.languageapp.backend.service.SseService;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ordinary mvn test must not start Hibernate/seeding against the developer's configured database. */
@EnabledIfEnvironmentVariable(named = "APPLICATION_SMOKE_TEST_DATABASE_URL",
        matches = "jdbc:postgresql://[^/]+/languageapp_smoke_check_[a-zA-Z0-9_]+")
@SpringBootTest(properties = {
        "spring.datasource.url=${APPLICATION_SMOKE_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=create", "spring.jpa.show-sql=false",
        "jwt.secret=U2VjdXJpdHkgZm91bmRhdGlvbnMgdGVzdCBvbmx5IHNpZ25pbmcga2V5IG5vdCBmb3IgZGVwbG95bWVudCE="
})
class BackendApplicationTests {
    @Autowired DataSource dataSource;
    @MockitoBean DataSeeder seeder;
    @MockitoBean EmailService mail;
    @MockitoBean ImageStorageService images;
    @MockitoBean SseService sse;
    @MockitoBean AssignmentExpiryScheduler expiry;
    @MockitoBean ChallengeScheduler challenges;

    @Test
    void contextLoadsOnlyAgainstTheExplicitDisposableDatabase() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().matches(
                    "jdbc:postgresql://[^/]+/languageapp_smoke_check_[a-zA-Z0-9_]+"));
            assertTrue(connection.getCatalog().startsWith("languageapp_smoke_check_"));
        }
    }
}
