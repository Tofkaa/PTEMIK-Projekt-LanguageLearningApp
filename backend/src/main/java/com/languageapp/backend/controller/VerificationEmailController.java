package com.languageapp.backend.controller;

import com.languageapp.backend.service.VerificationEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class VerificationEmailController {
    private final VerificationEmailService service;
    @PostMapping("/api/users/me/verification-email")
    public ResponseEntity<Void> resend(Authentication authentication) {
        service.resend(authentication.getName());
        return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
}
