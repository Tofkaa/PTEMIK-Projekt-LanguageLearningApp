package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.ExerciseResponse;
import com.languageapp.backend.dto.response.LessonResponse;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.exception.ForbiddenException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.ChallengeRepository;
import com.languageapp.backend.repository.CourseRepository;
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

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service responsible for managing and retrieving educational content.
 * <p>
 * Ensures that sensitive data (like correct answers) is stripped by mapping
 * entities to safe Data Transfer Objects (DTOs) before returning them to the controller.
 * Implements the dynamic difficulty engine by filtering exercises based on the user's level.
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
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public List<LessonResponse> getAllLessonsForUser(String userEmail) {
        log.debug("Fetching ordered lessons for user: {}", userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        UUID activeCourseId = user.getActiveCourse().getCourseId();

        // 1. Határozzuk meg az aktuális adaptív szintet, amit a kártyákon mutatni fogunk
        String currentDifficulty = userDifficultyCalculator.determineTargetDifficulty(user);

        // 2. Kérjük le a felhasználó ÖSSZES eddigi eredményét egyetlen query-ben
        List<Progress> userProgress = progressRepository.findByUserUserId(user.getUserId());

        // 3. Magyar nyelvű ABC rendező (hogy az Ételek ne kerüljön a végére)
        Collator huCollator = Collator.getInstance(new Locale("hu", "HU"));

        List<Lesson> courseLessons = lessonRepository.findByTopic_Course_CourseId(activeCourseId)
                .stream()
                .filter(lesson -> lesson.isActive() && lesson.getTopic().isActive())
                .sorted((l1, l2) -> {
                    // Témakörök betűrendben (pl. Alapvető -> Családtagok -> Ételek -> Utazás)
                    int topicCompare = huCollator.compare(l1.getTopic().getName(), l2.getTopic().getName());
                    if (topicCompare != 0) return topicCompare;

                    // Témakörön belül az új orderIndex alapján
                    int idx1 = l1.getOrderIndex() != null ? l1.getOrderIndex() : 999;
                    int idx2 = l2.getOrderIndex() != null ? l2.getOrderIndex() : 999;
                    return Integer.compare(idx1, idx2);
                })
                .toList();

        return courseLessons.stream()
                .map(lesson -> {
                    // SEAMLESS HALADÁS JAVÍTÁSA:
                    // Ha a leckét BÁRMILYEN nehézségen befejezte már, akkor a fa haladjon tovább!
                    boolean isCompleted = userProgress.stream()
                            .filter(p -> p.getLesson().getLessonId().equals(lesson.getLessonId()))
                            .anyMatch(Progress::getIsCompleted);

                    // Keressük meg a legutolsó sikeres szintet, amivel már teljesítette
                    String completedDiff = userProgress.stream()
                            .filter(p -> p.getLesson().getLessonId().equals(lesson.getLessonId()) && p.getIsCompleted())
                            .map(Progress::getCompletedDifficulty)
                            .findFirst()
                            .orElse(null);

                    return new LessonResponse(
                            lesson.getLessonId(),
                            lesson.getTopic().getName(),
                            lesson.getTitle(),
                            lesson.getOrderIndex(),
                            lesson.getLanguage(),
                            lesson.getDescription(),
                            isCompleted,
                            completedDiff,
                            currentDifficulty // Ezt látja a Dashboard a kártyákon
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExerciseResponse> getExercisesByLessonId(UUID lessonId, String userEmail, UUID challengeId) {
        log.debug("Fetching exercises for lesson ID: {} for user: {}", lessonId, userEmail);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("No lesson found with this ID!"));

        if (!lesson.isActive() || !lesson.getTopic().isActive()) {
            throw new ForbiddenException("This lesson is currently unavailable.");
        }

        if (challengeId != null) {
            Challenge challenge = challengeRepository.findById(challengeId)
                    .orElseThrow(() -> new ForbiddenException("Challenge does not exist!"));

            if (!challenge.getChallenger().getUserId().equals(user.getUserId()) &&
                    !challenge.getOpponent().getUserId().equals(user.getUserId())) {
                throw new ForbiddenException("You are not a member of this challenge!");
            }

            if (!challenge.getLesson().getLessonId().equals(lessonId)) {
                throw new ForbiddenException("This challenge belongs to a different lesson!");
            }

            String challengeDifficulty = challenge.getTargetDifficulty();
            if (challengeDifficulty == null || challengeDifficulty.isBlank() || "DYNAMIC".equalsIgnoreCase(challengeDifficulty)) {
                challengeDifficulty = userDifficultyCalculator.determineTargetDifficulty(challenge.getChallenger());
            }

            final String finalChallengeDiff = challengeDifficulty.trim();

            return lesson.getExercises().stream()
                    .filter(Exercise::isActive)
                    .filter(exercise -> finalChallengeDiff.equalsIgnoreCase(exercise.getDifficulty()))
                    .map(this::mapToExerciseResponse)
                    .toList();
        }


        if (Role.TEACHER.equals(user.getRole()) || Role.ADMIN.equals(user.getRole())) {
            return lesson.getExercises().stream()
                    .filter(Exercise::isActive)
                    .map(this::mapToExerciseResponse)
                    .toList();
        }


        final String finalTargetDifficulty = userDifficultyCalculator.determineTargetDifficulty(user);

        return lesson.getExercises().stream()
                .filter(Exercise::isActive)
                .filter(exercise -> finalTargetDifficulty.equalsIgnoreCase(exercise.getDifficulty()))
                .map(this::mapToExerciseResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> getAllLessonsForChallengeDropdown(String userEmail, String requestedCourseCode) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        UUID targetCourseId;

        if (requestedCourseCode != null && !requestedCourseCode.isBlank()) {
            Course requestedCourse = courseRepository.findByLanguageCode(requestedCourseCode.toLowerCase().trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Requested course not found: " + requestedCourseCode));
            targetCourseId = requestedCourse.getCourseId();
        } else {
            targetCourseId = user.getActiveCourse().getCourseId();
        }

        return lessonRepository.findByTopic_Course_CourseId(targetCourseId).stream()
                .filter(lesson -> lesson.isActive() && lesson.getTopic().isActive())
                .map(lesson -> new LessonResponse(
                        lesson.getLessonId(),
                        lesson.getTopic().getName(),
                        lesson.getTitle(),
                        lesson.getOrderIndex(),
                        lesson.getLanguage(),
                        lesson.getDescription(),
                        false,
                        null,
                        "DYNAMIC" // A dropdownban nem releváns
                ))
                .toList();
    }

    private ExerciseResponse mapToExerciseResponse(Exercise exercise) {
        String hash = null;

        if (!"TRANSLATION".equals(exercise.getType()) &&
                exercise.getCorrectAnswer() != null &&
                exercise.getCorrectAnswer().containsKey("answer")) {

            String rawAnswer = String.valueOf(exercise.getCorrectAnswer().get("answer"));
            String normalizedAnswer = rawAnswer.toLowerCase().trim();
            hash = generateSha256Hash(normalizedAnswer + exerciseSalt);
        }

        return new ExerciseResponse(
                exercise.getExerciseId(),
                exercise.getLesson().getLessonId(),
                exercise.getType(),
                exercise.getDifficulty(),
                exercise.getContent(),
                exercise.getAudioUrl(),
                exercise.getImageUrl(),
                hash
        );
    }

    private String generateSha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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
            log.error("Critical error while generating hash!", e);
            return null;
        }
    }
}