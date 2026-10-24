package com.languageapp.backend.controller;

import com.languageapp.backend.service.AccountDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/deletion")
@RequiredArgsConstructor
public class AccountDeletionController {
    private final AccountDeletionService service;
    public record Confirmation(String token, Boolean confirmed) {}

    @PostMapping("/request")
    public ResponseEntity<AccountDeletionService.Preview> request(Authentication auth,
            @CookieValue(name = "refreshToken", required = false) String session) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.request(auth.getName(), session));
    }

    // Validation is deliberately non-consuming. No GET endpoint performs any account operation.
    @PostMapping("/preview")
    public ResponseEntity<AccountDeletionService.Preview> preview(Authentication auth,
            @CookieValue(name = "refreshToken", required = false) String session, @RequestBody Confirmation request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.preview(auth.getName(), session, request.token()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(Authentication auth,
            @CookieValue(name = "refreshToken", required = false) String session, @RequestBody Confirmation request) {
        service.confirm(auth.getName(), session, request.token(), Boolean.TRUE.equals(request.confirmed()));
        ResponseCookie cleared = ResponseCookie.from("refreshToken", "").httpOnly(true).secure(true)
                .path("/").sameSite("Lax").maxAge(0).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
