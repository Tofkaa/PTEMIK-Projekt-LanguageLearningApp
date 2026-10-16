package com.languageapp.backend.dto;

import com.languageapp.backend.dto.request.AssignmentCreateRequest;
import com.languageapp.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TimeApiTest {
    private MockMvc mvc;
    private static final Instant NOW = Instant.parse("2026-10-16T16:00:00Z");

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TimeEchoController())
                .setControllerAdvice(new GlobalExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC))).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-16T16:00:00Z", "2026-10-16T18:00:00+02:00"})
    void timestampRequest_ShouldReturnAnExplicitUtcInstant(String value) throws Exception {
        mvc.perform(post("/time").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availableFrom\":\"" + value + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableFrom").value(NOW.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-16T16:00:00", "invalid", "2026-02-30T16:00:00Z"})
    void invalidTimestampRequest_ShouldReturn400WithUtcErrorTimestamp(String value) throws Exception {
        mvc.perform(post("/time").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availableFrom\":\"" + value + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").value(NOW.toString()));
    }

    @RestController
    static class TimeEchoController {
        @PostMapping("/time")
        public AssignmentCreateRequest echo(@RequestBody AssignmentCreateRequest body) {
            return body;
        }
    }
}
