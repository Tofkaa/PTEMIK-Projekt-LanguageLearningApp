package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.ExerciseResponse;
import com.languageapp.backend.dto.response.LessonResponse;
import com.languageapp.backend.entity.Exercise;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.Result;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.ForbiddenException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.ResultRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service responsible for managing and retrieving educational content.
 * <p>
 * Ensures that sensitive data (like correct answers) is stripped by mapping
 * entities to safe Data Transfer Objects (DTOs) before returning them to the controller.
 * Also handles adaptive learning logic to serve difficulty-appropriate lessons.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final UserDifficultyCalculator userDifficultyCalculator;

    /**
     * Retrieves all lessons filtered by the user's preferred or dynamically calculated difficulty.
     *
     * @param userEmail the email of the authenticated user
     * @return a list of {@link LessonResponse}
     */
    @Transactional(readOnly = true)
    public List<LessonResponse> getAllLessonsForUser(String userEmail) {
        log.debug("Fetching tailored lessons for user: {}", userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        // 1. Calculate target difficulty
        String targetDifficulty = userDifficultyCalculator.determineTargetDifficulty(user);
        log.info("Target difficulty for user {} is set to: {}", userEmail, targetDifficulty);

        // 2. Only get the correct lessons for desired difficulty
        List<Lesson> tailoredLessons = lessonRepository.findByDifficulty(targetDifficulty);

        return tailoredLessons.stream()
                .map(this::mapToLessonResponse)
                .toList();
    }

    /**
     * Retrieves all exercises for a specific lesson, strictly omitting the correct answers.
     * Includes IDOR security checks to prevent students from accessing restricted difficulty levels.
     *
     * @param lessonId the unique identifier of the lesson
     * @param userEmail the email of the authenticated user requesting the exercises
     * @return a list of {@link ExerciseResponse}
     */
    @Transactional(readOnly = true)
    public List<ExerciseResponse> getExercisesByLessonId(UUID lessonId, String userEmail) {
        log.debug("Fetching safe exercises for lesson ID: {} for user: {}", lessonId, userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> {
                    log.warn("Fetch failed: Lesson not found with ID {}", lessonId);
                    return new ResourceNotFoundException("No lesson found with this ID!");
                });

        if ("STUDENT".equals(user.getRole())) {
            String allowedDifficulty = userDifficultyCalculator.determineTargetDifficulty(user);

            if (!lesson.getDifficulty().equals(allowedDifficulty)) {
                log.warn("SECURITY ALERT: User {} attempted to bypass difficulty settings! Requested: {}, Allowed: {}",
                        userEmail, lesson.getDifficulty(), allowedDifficulty);
                throw new ForbiddenException("Access denied: lesson difficulty does not match preferred difficulty!.");
            }
        }

        return lesson.getExercises().stream()
                .map(this::mapToExerciseResponse)
                .toList();
    }

    private LessonResponse mapToLessonResponse(Lesson lesson) {
        return new LessonResponse(
                lesson.getLessonId(),
                lesson.getTopic().getName(),
                lesson.getTitle(),
                lesson.getDifficulty(),
                lesson.getLanguage(),
                lesson.getDescription()
        );
    }

    private ExerciseResponse mapToExerciseResponse(Exercise exercise) {
        return new ExerciseResponse(
                exercise.getExerciseId(),
                exercise.getLesson().getLessonId(),
                exercise.getType(),
                exercise.getContent(),
                exercise.getAudioUrl(),
                exercise.getImageUrl()
        );
    }
}