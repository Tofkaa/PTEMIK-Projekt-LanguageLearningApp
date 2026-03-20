package com.languageapp.backend.controller;

import com.languageapp.backend.dto.response.AchievementResponse;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.service.AchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller responsible for handling gamification and achievement-related requests.
 * Provides endpoints for users to view their unlocked and locked trophies.
 */
@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;
    private final UserRepository userRepository;

    /**
     * Retrieves the complete list of achievements for the currently authenticated user.
     * Determines which achievements are unlocked based on the user's history.
     *
     * @param authentication The Spring Security authentication object containing the user's JWT details.
     * @return A list of AchievementResponse DTOs.
     */
    @GetMapping("/me")
    public ResponseEntity<List<AchievementResponse>> getMyAchievements(Authentication authentication) {
        // Extract the user's email from the SecurityContext (JWT token)
        String email = authentication.getName();

        // Safely retrieve the full User entity from the database to avoid Detached Entity issues
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Delegate the business logic to the service layer using the validated User ID
        return ResponseEntity.ok(achievementService.getUserAchievements(user.getUserId()));
    }
}