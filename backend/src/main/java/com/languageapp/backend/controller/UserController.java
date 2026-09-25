package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.UserPreferencesRequest;
import com.languageapp.backend.dto.response.ProgressResponse;
import com.languageapp.backend.dto.response.UserResponse;
import com.languageapp.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PutMapping("/me/preferences")
    public ResponseEntity<String> updatePreferences(@RequestBody UserPreferencesRequest request, Authentication authentication) {
        userService.updateUserPreferences(authentication.getName(), request.getPreferredDifficulty());
        return ResponseEntity.ok("Preferences updated successfully");
    }

    /**
     * Uploads and updates the authenticated user's profile picture.
     */
    @PostMapping(value = "/me/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadProfilePicture(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            return ResponseEntity.badRequest().body("Érvénytelen fájl! Csak képformátum (JPG, PNG, stb.) engedélyezett.");
        }
        log.info("REST request to upload profile picture for user: {}", authentication.getName());

        String newImageUrl = userService.updateProfilePicture(authentication.getName(), file);

        return ResponseEntity.ok(newImageUrl);
    }
}