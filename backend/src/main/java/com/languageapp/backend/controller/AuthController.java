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
 * <p>
 * Provides endpoints for user registration and login.
 * On successful authentication, a refresh token is issued
 * as an HTTP-only cookie.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * Registers a new user account.
     *
     * @param request contains user registration data
     * @return {@link ResponseEntity} containing authentication response
     *         and a refresh token stored in an HTTP-only cookie
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthenticationService.AuthResult result = authenticationService.register(request);

        ResponseCookie cookie = createCookie(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.responseDto());
    }

    /**
     * Authenticates an existing user.
     *
     * @param request contains login credentials
     * @return {@link ResponseEntity} containing authentication response
     *         and a refresh token stored in an HTTP-only cookie
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.AuthResult result = authenticationService.authenticate(request);

        ResponseCookie cookie = createCookie(result.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.responseDto());
    }

    /**
     * Refreshes the JWT Access Token using the HttpOnly Refresh Token cookie.
     *
     * @param refreshToken the refresh token automatically sent by the browser
     * @return {@link ResponseEntity} containing the new access token
     */
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

    /**
     * Creates a refresh token cookie with secure attributes.
     *
     * @param refreshToken JWT refresh token value
     * @return configured {@link ResponseCookie}
     */
    private ResponseCookie createCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Strict")
                .build();
    }
}