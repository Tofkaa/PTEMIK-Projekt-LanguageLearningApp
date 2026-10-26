package com.languageapp.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.util.Base64;
import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwtOnly.class).withPropertyValues("jwt.expiration=900000");

    @Configuration(proxyBeanMethods = false)
    static class JwtOnly {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean JwtService jwtService(Clock clock) { return new JwtService(clock); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "sensitive-invalid-input??", "c2hvcnQ="})
    void invalidSecretFailsAtStartupWithoutPrintingItsValue(String supplied) {
        runner.withPropertyValues("jwt.secret=" + supplied).run(context -> {
            assertThat(context).hasFailed();
            var output = new StringWriter();
            context.getStartupFailure().printStackTrace(new PrintWriter(output));
            assertThat(output.toString()).contains("JWT_SECRET");
            if (!supplied.isBlank()) assertThat(output.toString()).doesNotContain(supplied);
        });
    }

    @Test void productionConfigurationHasNoBuiltInKeyWhenTheEnvironmentValueIsMissing() throws Exception {
        // Read the compiled MAIN resource, not the test-only application.properties shadowing it.
        var production = new java.util.Properties();
        var location = JwtService.class.getProtectionDomain().getCodeSource().getLocation();
        try (var input = location.toURI().resolve("application.properties").toURL().openStream()) { production.load(input); }
        runner.withPropertyValues("jwt.secret=" + production.getProperty("jwt.secret"), "JWT_SECRET=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void aValidKeyStartsAndIssuesVerifiableTokens() {
        String testKey = Base64.getEncoder().encodeToString(new byte[64]);
        runner.withPropertyValues("jwt.secret=" + testKey).run(context -> {
            assertThat(context).hasNotFailed();
            JwtService service = context.getBean(JwtService.class);
            var user = org.springframework.security.core.userdetails.User.withUsername("test@example.test")
                    .password("unused").roles("STUDENT").build();
            assertThat(service.isTokenValid(service.generateToken(user), user)).isTrue();
        });
    }
}
