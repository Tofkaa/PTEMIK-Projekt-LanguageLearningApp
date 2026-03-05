package com.languageapp.backend.controller;

import com.languageapp.backend.dto.response.ProgressResponse;
import com.languageapp.backend.dto.response.UserResponse;
import com.languageapp.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing user profiles and tracking progress.
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Retrieves the profile of the currently authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUserProfile(Authentication authentication) {
        log.info("REST request to get profile for authenticated user");
        return ResponseEntity.ok(userService.getUserProfile(authentication.getName()));
    }

    /**
     * Retrieves the learning progress of the currently authenticated user.
     */
    @GetMapping("/me/progress")
    public ResponseEntity<List<ProgressResponse>> getCurrentUserProgress(Authentication authentication) {
        log.info("REST request to get progress for authenticated user");
        return ResponseEntity.ok(userService.getUserProgress(authentication.getName()));
    }
}