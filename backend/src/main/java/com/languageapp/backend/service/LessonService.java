package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.ExerciseResponse;
import com.languageapp.backend.dto.response.LessonResponse;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.ForbiddenException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.ChallengeRepository;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.repository.ProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;

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

    @Value("${app.security.exercise-salt}")
   private String exerciseSalt;

    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final UserDifficultyCalculator userDifficultyCalculator;
    private final ProgressRepository progressRepository;
    private final ChallengeRepository challengeRepository;

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
                .map(lesson -> mapToLessonResponse(lesson, user))
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
    public List<ExerciseResponse> getExercisesByLessonId(UUID lessonId, String userEmail, UUID challengeId) {
        log.debug("Fetching safe exercises for lesson ID: {} for user: {}", lessonId, userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> {
                    log.warn("Fetch failed: Lesson not found with ID {}", lessonId);
                    return new ResourceNotFoundException("No lesson found with this ID!");
                });

        if ("STUDENT".equals(user.getRole())) {
            // --- BYPASS LOGIKA ---
            if (challengeId != null) {
                Challenge challenge = challengeRepository.findById(challengeId)
                        .orElseThrow(() -> new ForbiddenException("Kihívás nem létezik!"));

                // Csak a résztvevők mehetnek be
                if (!challenge.getChallenger().getUserId().equals(user.getUserId()) &&
                        !challenge.getOpponent().getUserId().equals(user.getUserId())) {
                    throw new ForbiddenException("Nem vagy tagja ennek a kihívásnak!");
                }

                // Csak a saját leckéjével
                if (!challenge.getLesson().getLessonId().equals(lessonId)) {
                    throw new ForbiddenException("Ez a kihívás egy másik leckére szól!");
                }
            }
        }

        return lesson.getExercises().stream()
                .map(this::mapToExerciseResponse)
                .toList();
    }

    /**
     * Returns ALL lessons (without difficulty filter) to the Challenges Modal dropdown.
     * This is safe because it only reveals the names and IDs, not the lesson content!
     */
    @Transactional(readOnly = true)
    public List<LessonResponse> getAllLessonsForChallengeDropdown() {
        return lessonRepository.findAll().stream()
                .map(lesson -> new LessonResponse(
                        lesson.getLessonId(),
                        lesson.getTopic().getName(),
                        lesson.getTitle(),
                        lesson.getDifficulty(),
                        lesson.getLanguage(),
                        lesson.getDescription(),
                        false
                ))
                .toList();
    }

    private LessonResponse mapToLessonResponse(Lesson lesson, User user) {
        boolean isCompleted = progressRepository
                .findByUserUserIdAndLessonLessonId(user.getUserId(), lesson.getLessonId())
                .map(Progress::getIsCompleted)
                .orElse(false);

        return new LessonResponse(
                lesson.getLessonId(),
                lesson.getTopic().getName(),
                lesson.getTitle(),
                lesson.getDifficulty(),
                lesson.getLanguage(),
                lesson.getDescription(),
                isCompleted
        );
    }

    private ExerciseResponse mapToExerciseResponse(Exercise exercise) {
        String hash = null;

        // Only generate hashes for non translation exercises that have a correct answer for them
        if (!"TRANSLATION".equals(exercise.getType()) &&
                exercise.getCorrectAnswer() != null &&
                exercise.getCorrectAnswer().containsKey("answer")) {

            String rawAnswer = String.valueOf(exercise.getCorrectAnswer().get("answer"));
            // Normalize the answer
            String normalizedAnswer = rawAnswer.toLowerCase().trim();
            hash = generateSha256Hash(normalizedAnswer + exerciseSalt);
        }

        return new ExerciseResponse(
                exercise.getExerciseId(),
                exercise.getLesson().getLessonId(),
                exercise.getType(),
                exercise.getContent(),
                exercise.getAudioUrl(),
                exercise.getImageUrl(),
                hash
        );
    }


    /**
     * SHA-256 based hash generator to support client side validation.
     */
    private String generateSha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert byte array to hexadecimal String
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Kritikus hiba a hash generálása közben!", e);
            return null;
        }
    }
}