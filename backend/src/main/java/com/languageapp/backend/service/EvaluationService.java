package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ExerciseSubmission;
import com.languageapp.backend.dto.request.LessonSubmitRequest;
import com.languageapp.backend.dto.response.LessonSubmitResponse;
import com.languageapp.backend.dto.response.MistakeDTO;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.ForbiddenException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.ProgressRepository;
import com.languageapp.backend.repository.ResultRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service responsible for evaluating user submissions for lessons.
 * <p>
 * Handles answer verification, score calculation, experience points (XP) distribution,
 * and updates to the user's progress and historical results.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private static final int PASSING_SCORE_THRESHOLD = 60;
    private static final int XP_PER_CORRECT_ANSWER = 10;

    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final ResultRepository resultRepository;
    private final ProgressRepository progressRepository;
    private final UserDifficultyCalculator userDifficultyCalculator;

    @Transactional
    public LessonSubmitResponse evaluateLesson(UUID userId, UUID lessonId, LessonSubmitRequest request) {
        log.debug("Starting evaluation for user: {} and lesson: {}", userId, lessonId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found"));

        validateUserDifficultyAccess(user, lesson);

        List<Exercise> exercises = lesson.getExercises();
        int totalQuestions = exercises.size();

        List<MistakeDTO> mistakes = new ArrayList<>();
        int correctAnswersCount = calculateCorrectAnswers(exercises, request, mistakes);

        int score = totalQuestions == 0 ? 0 : (int) Math.round(((double) correctAnswersCount / totalQuestions) * 100);
        boolean passed = score >= PASSING_SCORE_THRESHOLD;

        Progress progress = progressRepository.findByUserUserIdAndLessonLessonId(userId, lessonId)
                .orElseGet(() -> {
                    Progress newProgress = new Progress();
                    newProgress.setUser(user);
                    newProgress.setLesson(lesson);
                    newProgress.setHighestScore(0);
                    newProgress.setIsCompleted(false);
                    return newProgress;
                });

        int xpEarned = 0;
        if (passed && !progress.getIsCompleted()) {
            xpEarned = correctAnswersCount * XP_PER_CORRECT_ANSWER;
            user.setXp(user.getXp() + xpEarned);
            userRepository.save(user);
            log.info("User {} earned {} XP. Total XP: {}", user.getEmail(), xpEarned, user.getXp());
        } else if (passed && progress.getIsCompleted()) {
            log.debug("User {} already completed this lesson. No new XP awarded.", user.getEmail());
        }

        Result savedResult = saveResult(user, lesson, request, correctAnswersCount, totalQuestions, score);

        updateProgress(progress, score, passed);

        String feedback = passed ? "Congratulations! You passed the lesson." : "Keep practicing! You can do better.";

        return LessonSubmitResponse.builder()
                .resultId(savedResult.getResultId())
                .score(score)
                .correctAnswersCount(correctAnswersCount)
                .totalQuestionsCount(totalQuestions)
                .xpEarned(xpEarned)
                .passed(passed)
                .feedback(feedback)
                .mistakes(mistakes)
                .build();
    }

    private void validateUserDifficultyAccess(User user, Lesson lesson) {
        if ("STUDENT".equals(user.getRole())) {
            String allowedDifficulty = userDifficultyCalculator.determineTargetDifficulty(user);
            if (!lesson.getDifficulty().equals(allowedDifficulty)) {
                log.warn("SECURITY ALERT: User {} attempted to SUBMIT restricted difficulty! Requested: {}, Allowed: {}",
                        user.getEmail(), lesson.getDifficulty(), allowedDifficulty);
                throw new ForbiddenException("Access denied : you cant access this difficulty!");
            }
        }
    }

    private int calculateCorrectAnswers(List<Exercise> exercises, LessonSubmitRequest request, List<MistakeDTO> mistakes) {
        Map<UUID, String> submittedAnswers = request.getAnswers().stream()
                .collect(Collectors.toMap(
                        ExerciseSubmission::getExerciseId,
                        submission -> String.valueOf(submission.getAnswer()),
                        (existing, replacement) -> existing
                ));

        int correctCount = 0;
        for (Exercise exercise : exercises) {
            if (exercise.getCorrectAnswer() != null && exercise.getCorrectAnswer().containsKey("answer")) {

                String rawExpected = String.valueOf(exercise.getCorrectAnswer().get("answer"));
                String rawSubmitted = submittedAnswers.getOrDefault(exercise.getExerciseId(), "");

                String cleanExpected = normalizeText(rawExpected);
                String cleanUser = normalizeText(rawSubmitted);

                if (cleanExpected.equals(cleanUser)) {
                    correctCount++;
                } else {
                    String questionText = exercise.getContent() != null && exercise.getContent().containsKey("question")
                            ? String.valueOf(exercise.getContent().get("question"))
                            : "Unknown question";

                    mistakes.add(new MistakeDTO(questionText, rawSubmitted, rawExpected));
                }
            }
        }
        return correctCount;
    }

    private Result saveResult(User user, Lesson lesson, LessonSubmitRequest request,
                              int correctCount, int totalCount, int score) {
        Result result = new Result();
        result.setUser(user);
        result.setLesson(lesson);
        result.setScore(score);
        result.setTimeTaken(request.getTimeTakenSeconds());
        result.setSubmittedAt(LocalDateTime.now());
        result.setCorrectAnswersCount(correctCount);
        result.setTotalQuestionsCount(totalCount);
        result.setIsTestResult(false);
        result.setIsChallengeResult(false);

        return resultRepository.save(result);
    }

    private void updateProgress(Progress progress, int score, boolean passed) {
        progress.setLastAttemptAt(LocalDateTime.now());

        if (score > progress.getHighestScore()) {
            progress.setHighestScore(score);
        }

        if (passed && !progress.getIsCompleted()) {
            progress.setIsCompleted(true);
            progress.setCompletedAt(LocalDateTime.now());
        }

        progressRepository.save(progress);
    }

    /**
     * Normalizes a string for comparison by removing all punctuation,
     * converting to lowercase, and collapsing multiple spaces into a single space.
     */
    private String normalizeText(String input) {
        if (input == null || input.isBlank() || "null".equals(input)) {
            return "";
        }
        return input
                .replaceAll("[\\p{Punct}]", "")
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
    }
}