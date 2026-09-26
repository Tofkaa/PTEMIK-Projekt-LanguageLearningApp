package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.ProgressResponse;
import com.languageapp.backend.dto.response.UserResponse;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.DifficultyLevel;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.ProgressRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import java.util.List;

/**
 * Service handling user profile and progress retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProgressRepository progressRepository;
    private final ImageStorageService imageStorageService;

    /**
     * Retrieves the profile information of the authenticated user.
     *
     * @param email the email extracted from the security token
     * @return a safe {@link UserResponse} DTO
     */
    @Transactional(readOnly = true)
    public UserResponse getUserProfile(String email) {
        log.debug("Fetching profile for user: {}", email);

        User user = getUserByEmail(email);

        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .xp(user.getXp())
                .streak(user.getStreak())
                .preferredDifficulty(user.getPreferredDifficulty().name())
                .userTag(user.getUserTag())
                .friendCode(user.getFriendCode())
                .isVerified(user.isVerified())
                .profilePictureUrl(user.getProfilePictureUrl())
                .build();
    }

    /**
     * Retrieves the learning progress of the authenticated user.
     *
     * @param email the email extracted from the security token
     * @return a list of {@link ProgressResponse} DTOs
     */
    @Transactional(readOnly = true)
    public List<ProgressResponse> getUserProgress(String email) {
        log.debug("Fetching progress for user: {}", email);

        User user = getUserByEmail(email);

        return progressRepository.findByUserUserId(user.getUserId()).stream()
                .map(progress -> ProgressResponse.builder()
                        .lessonId(progress.getLesson().getLessonId())
                        .lessonTitle(progress.getLesson().getTitle())
                        .highestScore(progress.getHighestScore())
                        .isCompleted(progress.getIsCompleted())
                        .lastAttemptAt(progress.getLastAttemptAt())
                        .completedAt(progress.getCompletedAt())
                        .build())
                .toList();
    }

    /**
     * Fetches a user by email or throws a standard ResourceNotFoundException.
     */
    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("Authenticated user not found in the database with email: {}", email);
                    return new ResourceNotFoundException("User not found");
                });
    }

    /**
     * Updates a user's preferences by email or throws a standard ResourceNotFoundException
     * @param email - The user's email address allocated to the account
     * @param newDifficulty - The new difficulty setting they wish to save to their profile
     */

    @Transactional
    public void updateUserPreferences(String email, String newDifficulty) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPreferredDifficulty(DifficultyLevel.valueOf(newDifficulty));
        userRepository.save(user);
        log.info("User {} updated preferred difficulty to {}", email, newDifficulty);
    }
    @Transactional
    public String updateProfilePicture(String email, MultipartFile file) {
        User user = getUserByEmail(email);

        try {

            String imageUrl = imageStorageService.uploadProfileImage(file);

            user.setProfilePictureUrl(imageUrl);
            userRepository.save(user);

            log.info("User {} updated profile picture", email);
            return imageUrl;
        } catch (IOException e) {
            log.error("Failed to upload profile picture for user {}: {}", email, e.getMessage());
            throw new RuntimeException("Nem sikerült feltölteni a képet. Kérlek, próbáld újra.");
        }
    }

    /**
     * Updates the user's display name and generates a new unique 4-digit userTag.
     */
    @Transactional
    public UserResponse updateUserName(String email, String newName) {
        if (newName == null || newName.trim().length() < 3) {
            throw new BadRequestException("A felhasználónévnek legalább 3 karakter hosszúnak kell lennie!");
        }

        String trimmedName = newName.trim();
        User user = getUserByEmail(email);

        if (trimmedName.equals(user.getName())) {
            throw new BadRequestException("Az új felhasználónév nem lehet azonos a jelenlegivel!");
        }

        String generatedTag;
        int attempts = 0;
        java.util.Random random = new java.util.Random();
        do {
            int randomTag = 1000 + random.nextInt(9000);
            generatedTag = String.valueOf(randomTag);
            attempts++;
            if (attempts > 100) {
                throw new BadRequestException("Túl sok felhasználó van ezzel a névvel. Kérlek, válassz egy egyedibb nevet!");
            }
        } while (userRepository.existsByNameAndUserTag(trimmedName, generatedTag));

        user.setName(trimmedName);
        user.setUserTag(generatedTag);
        userRepository.save(user);

        log.info("User {} updated name to {}#{}", email, trimmedName, generatedTag);

        return getUserProfile(email);
    }
}