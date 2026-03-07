package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.ProgressResponse;
import com.languageapp.backend.dto.response.UserResponse;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.ProgressRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}