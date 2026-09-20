package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.LoginRequest;
import com.languageapp.backend.dto.request.RegisterRequest;
import com.languageapp.backend.dto.response.AuthResponse;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.security.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller responsible for authentication-related operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthenticationService.AuthResult result = authenticationService.register(request);

        ResponseCookie cookie = createCookie(result.refreshToken(), false);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.responseDto());
    }

    /**
     * Endpoint to verify email based on token
     */
    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        log.info("Received email verification request for token");
        authenticationService.verifyEmail(token);
        return ResponseEntity.ok("E-mail cím sikeresen megerősítve!");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.AuthResult result = authenticationService.authenticate(request);

        ResponseCookie cookie = createCookie(result.refreshToken(), request.isRememberMe());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.responseDto());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            log.warn("Token refresh failed: Missing refreshToken cookie.");
            throw new BadRequestException("Missing refresh token, please login again.");
        }

        AuthResponse response = authenticationService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {

        String loggedOutUser = "Unknown/Guest";

        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            loggedOutUser = authenticationService.logout(refreshToken);
        }

        ResponseCookie deadCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        log.info("User: {} successfully logged out, cookie invalidated.", loggedOutUser);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deadCookie.toString())
                .build();
    }

    private ResponseCookie createCookie(String refreshToken, boolean rememberMe) {
        long maxAgeInSeconds = rememberMe ? (7 * 24 * 60 * 60) : -1;

        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(maxAgeInSeconds)
                .sameSite("Strict")
                .build();
    }
}