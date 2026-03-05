package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.ExerciseResponse;
import com.languageapp.backend.dto.response.LessonResponse;
import com.languageapp.backend.entity.Exercise;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.Result;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.ResultRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ResultRepository resultRepository;

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
        String targetDifficulty = determineTargetDifficulty(user);
        log.info("Target difficulty for user {} is set to: {}", userEmail, targetDifficulty);

        // 2. Only get the correct lessons for desired difficulty
        List<Lesson> tailoredLessons = lessonRepository.findByDifficulty(targetDifficulty);

        return tailoredLessons.stream()
                .map(this::mapToLessonResponse)
                .toList();
    }

    /**
     * Core Business Logic for Adaptive Learning.
     * Determines the exact difficulty level based on user preference and past performance.
     */
    private String determineTargetDifficulty(User user) {
        // If the students preferred difficulty is not dynamic, respect their choice
        if (!"DYNAMIC".equals(user.getPreferredDifficulty().name())) {
            return user.getPreferredDifficulty().name();
        }

        // If it is dynamic, get their last 3 results.
        List<Result> recentResults = resultRepository.findTop3ByUserUserIdOrderBySubmittedAtDesc(user.getUserId());

        // If they do not have 3 results, start them from MEDIUM.
        if (recentResults.isEmpty()) {
            return "MEDIUM";
        }

        // Calculate average
        double averageScore = recentResults.stream()
                .mapToInt(Result::getScore)
                .average()
                .orElse(0.0);

        log.debug("User's average score over last 3 lessons: {}", averageScore);

        // Decision tree
        if (averageScore >= 85.0) {
            return "HARD";
        } else if (averageScore < 50.0) {
            return "EASY";
        } else {
            return "MEDIUM";
        }
    }

    /**
     * Retrieves all exercises for a specific lesson, strictly omitting the correct answers.
     *
     * @param lessonId the unique identifier of the lesson
     * @return a list of {@link ExerciseResponse}
     * @throws ResourceNotFoundException if the requested lesson does not exist
     */
    @Transactional(readOnly = true)
    public List<ExerciseResponse> getExercisesByLessonId(UUID lessonId) {
        log.debug("Fetching safe exercises for lesson ID: {}", lessonId);

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> {
                    log.warn("Fetch failed: Lesson not found with ID {}", lessonId);
                    return new ResourceNotFoundException("No lesson found with this ID!");
                });

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