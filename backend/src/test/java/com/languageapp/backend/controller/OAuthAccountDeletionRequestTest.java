package com.languageapp.backend.controller;

import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.GlobalExceptionHandler;
import com.languageapp.backend.service.AccountDeletionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Clock;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OAuthAccountDeletionRequestTest {
    AccountDeletionService service = mock(AccountDeletionService.class);
    MockMvc mvc;
    final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("existing@example.test", "unused");
    final Cookie session = new Cookie("refreshToken", "session");

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AccountDeletionController(service))
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC())).build();
    }

    @Test void requestUsesPrincipalAndCookieAndNeverReturnsTheSecret() throws Exception {
        when(service.request(auth.getName(), "session")).thenReturn(new AccountDeletionService.Preview(auth.getName(), Instant.parse("2026-10-24T14:00:00Z")));
        mvc.perform(post("/api/users/me/deletion/request").principal(auth).cookie(session)
                        .contentType("application/json").content("{\"email\":\"attacker@example.test\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.email").value(auth.getName())).andExpect(jsonPath("$.token").doesNotExist());
        verify(service).request(auth.getName(), "session");
    }

    @Test void previewOnlyCallsPreviewAndDoesNotClearSession() throws Exception {
        when(service.preview(auth.getName(), "session", "secret")).thenReturn(new AccountDeletionService.Preview(auth.getName(), Instant.parse("2026-10-24T14:00:00Z")));
        mvc.perform(post("/api/users/me/deletion/preview").principal(auth).cookie(session)
                        .contentType("application/json").content("{\"token\":\"secret\"}"))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).preview(auth.getName(), "session", "secret");
        verifyNoMoreInteractions(service);
    }

    @Test void successfulExplicitConfirmationClearsCookie() throws Exception {
        mvc.perform(post("/api/users/me/deletion/confirm").principal(auth).cookie(session)
                        .contentType("application/json").content("{\"token\":\"secret\",\"confirmed\":true}"))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().maxAge("refreshToken", 0)).andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true));
        verify(service).confirm(auth.getName(), "session", "secret", true);
    }

    @Test void missingConfirmationIsFalseAndRejectionDoesNotClearCookie() throws Exception {
        doThrow(new BadRequestException("Hiányzó megerősítés."))
                .when(service).confirm(auth.getName(), "session", "secret", false);
        mvc.perform(post("/api/users/me/deletion/confirm").principal(auth).cookie(session)
                        .contentType("application/json").content("{\"token\":\"secret\"}"))
                .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).confirm(auth.getName(), "session", "secret", false);
    }

    @Test void getCannotIssuePreviewOrConfirmAnything() throws Exception {
        for (String path : new String[]{"request", "preview", "confirm"}) {
            mvc.perform(get("/api/users/me/deletion/" + path).principal(auth).cookie(session))
                    .andExpect(status().isMethodNotAllowed());
        }
        verifyNoInteractions(service);
    }
}
