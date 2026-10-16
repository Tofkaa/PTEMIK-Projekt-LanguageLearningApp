package com.languageapp.backend.dto;

import com.languageapp.backend.dto.request.AssignmentCreateRequest;
import com.languageapp.backend.dto.response.AssignmentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentTimeContractTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    @Test
    void explicitOffsetInput_ShouldNormalizeToUtcAndSerializeWithZ() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            AssignmentCreateRequest request = mapper.readValue(
                    "{\"availableFrom\":\"2026-10-16T18:00:00+02:00\"}", AssignmentCreateRequest.class);
            assertEquals(Instant.parse("2026-10-16T16:00:00Z"), request.getAvailableFrom());

            AssignmentResponse response = new AssignmentResponse(null, "TimeZoneTest", null, false,
                    null, request.getAvailableFrom(), null, 0, false, false, false, false,
                    false, false, null, 0, false);
            var json = mapper.readTree(mapper.writeValueAsString(response));
            assertEquals("2026-10-16T16:00:00Z", json.get("availableFrom").asString());
            assertTrue(json.get("availableUntil").isNull());
        });
    }

    @Test
    void offsetlessInput_ShouldBeRejectedInsteadOfGuessingTimezone() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThrows(Exception.class, () -> mapper.readValue(
                    "{\"availableFrom\":\"2026-10-16T18:00:00\"}", AssignmentCreateRequest.class));
        });
    }
}
