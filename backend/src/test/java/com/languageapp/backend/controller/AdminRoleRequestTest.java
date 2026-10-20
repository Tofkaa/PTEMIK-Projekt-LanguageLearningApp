package com.languageapp.backend.controller;

import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.GlobalExceptionHandler;
import com.languageapp.backend.service.AdminService;
import com.languageapp.backend.service.CurriculumService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminRoleRequestTest {
    private final AdminService service = mock(AdminService.class);
    private final UUID userId = UUID.fromString("c82f02e9-968c-4065-8af7-2deb738a08ef");
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminController(mock(CurriculumService.class), service))
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC())).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "UNKNOWN"})
    void emptyOrInvalidRoleReturns400WithoutChangingUser(String role) throws Exception {
        mvc.perform(put("/api/admin/users/{id}/role", userId).param("newRole", role))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/admin/users/" + userId + "/role"));
        verifyNoInteractions(service);
    }

    @Test
    void missingRoleReturns400WithoutChangingUser() throws Exception {
        mvc.perform(put("/api/admin/users/{id}/role", userId))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void validRoleReachesService() throws Exception {
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "admin@example.test", "unused");
        mvc.perform(put("/api/admin/users/{id}/role", userId).param("newRole", "STUDENT")
                        .principal(authentication))
                .andExpect(status().isOk());
        verify(service).updateUserRole(userId, Role.STUDENT, "admin@example.test");
    }
}
