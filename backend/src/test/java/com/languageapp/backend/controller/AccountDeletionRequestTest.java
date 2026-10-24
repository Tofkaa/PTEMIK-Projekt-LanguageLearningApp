package com.languageapp.backend.controller;

import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.GlobalExceptionHandler;
import com.languageapp.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountDeletionRequestTest {
    private final UserService service = mock(UserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UserController(service))
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC())).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"password\":\"test-password\"}",
            "{\"password\":\"test-password\",\"confirmed\":false}"})
    void missingOrFalseConfirmationReachesServiceAsFalseAndReturnsItsRejection(String body) throws Exception {
        doThrow(new BadRequestException("Hiányzó megerősítés."))
                .when(service).deleteAndAnonymizeMyAccount("local@example.test", "test-password", false);
        mvc.perform(delete("/api/users/me").contentType("application/json").content(body)
                        .principal(new UsernamePasswordAuthenticationToken("local@example.test", "unused")))
                .andExpect(status().isBadRequest());
        verify(service).deleteAndAnonymizeMyAccount("local@example.test", "test-password", false);
    }

    @Test
    void confirmedRequestUsesAuthenticatedAccountRatherThanAnySubmittedEmail() throws Exception {
        mvc.perform(delete("/api/users/me").contentType("application/json")
                        .content("{\"password\":\"test-password\",\"confirmed\":true}")
                        .principal(new UsernamePasswordAuthenticationToken("local@example.test", "unused")))
                .andExpect(status().isNoContent());
        verify(service).deleteAndAnonymizeMyAccount("local@example.test", "test-password", true);
    }
}
