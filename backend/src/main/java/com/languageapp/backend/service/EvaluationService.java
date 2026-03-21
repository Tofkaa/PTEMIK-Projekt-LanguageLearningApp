package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ExerciseSubmission;
import com.languageapp.backend.dto.request.LessonSubmitRequest;
import com.languageapp.backend.dto.response.ExerciseCheckResponse;
import com.languageapp.backend.dto.response.LessonSubmitResponse;
import com.languageapp.backend.dto.response.MistakeDTO;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.ForbiddenException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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
    private final ExerciseRepository exerciseRepository;
    private final AchievementRepository achievementRepository;
    private final AchievementService achievementService;


    /**
     * Orchestrates the entire submission process for a completed lesson.
     * Validates security, calculates scores and XP, updates user progress, and saves the final result.
     *
     * @param userId   The ID of the user submitting the lesson.
     * @param lessonId The ID of the lesson being submitted.
     * @param request  The payload containing time taken and all answers.
     * @return LessonSubmitResponse containing the evaluation results for the frontend.
     */
    @Transactional
    public LessonSubmitResponse evaluateLesson(UUID userId, UUID lessonId, LessonSubmitRequest request) {
        log.debug("Starting evaluation for user: {} and lesson: {}", userId, lessonId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found"));

        // OPTIMIZATION: Fetch the user's progress exactly once to minimize database overhead.
        var existingProgressOpt = progressRepository.findByUserUserIdAndLessonLessonId(userId, lessonId);
        boolean hasStarted = existingProgressOpt.isPresent();

        // SECURITY: Ensure the user is not trying to hack the adaptive difficulty system
        validateUserDifficultyAccess(user, lesson, hasStarted);

        List<Exercise> exercises = lesson.getExercises();
        int totalQuestions = exercises.size();

        List<MistakeDTO> mistakes = new ArrayList<>();

        // Calculate the score and dynamic XP (accounting for retries)
        EvaluationDetails details = calculateEvaluationDetails(exercises, request, mistakes);
        int correctAnswersCount = details.getCorrectCount();
        int potentialXp = details.getPotentialXp();

        int score = totalQuestions == 0 ? 0 : (int) Math.round(((double) correctAnswersCount / totalQuestions) * 100);
        boolean passed = score >= PASSING_SCORE_THRESHOLD;

        // Utilize the previously fetched Progress, or initialize a new one if this is the first attempt
        Progress progress = existingProgressOpt.orElseGet(() -> {
            Progress newProgress = new Progress();
            newProgress.setUser(user);
            newProgress.setLesson(lesson);
            newProgress.setHighestScore(0);
            newProgress.setIsCompleted(false);
            return newProgress;
        });

        int xpEarned = 0;

        // Only award XP if the user passed and hadn't already completed this lesson in the past
        if (passed && !progress.getIsCompleted()) {
            xpEarned = potentialXp; // Assign the dynamically calculated XP

            user.setXp(user.getXp() + xpEarned);
            userRepository.save(user);
            log.info("User {} earned {} XP. Total XP: {}", user.getEmail(), xpEarned, user.getXp());
        } else if (passed && progress.getIsCompleted()) {
            log.debug("User {} already completed this lesson. No new XP awarded.", user.getEmail());
        }

        // Save historical result and update ongoing progress
        Result savedResult = saveResult(user, lesson, request, correctAnswersCount, totalQuestions, score);
        updateProgress(progress, score, passed);

        String feedback = passed ? "Congratulations! You passed the lesson." : "Keep practicing! You can do better.";

        achievementService.checkAndAwardAchievements(user, score);

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

    private void validateUserDifficultyAccess(User user, Lesson lesson, boolean hasStarted) {
        if ("STUDENT".equals(user.getRole())) {
            String allowedDifficulty = userDifficultyCalculator.determineTargetDifficulty(user);
            if (!lesson.getDifficulty().equals(allowedDifficulty) && !hasStarted) {
                log.warn("SECURITY ALERT: User {} attempted to SUBMIT restricted difficulty! Requested: {}, Allowed: {}",
                        user.getEmail(), lesson.getDifficulty(), allowedDifficulty);
                throw new ForbiddenException("Access denied : you cant access this difficulty!");
            }
        }
    }

    // --- CORE EVALUATION LOGIC ---

    /**
     * Evaluates submitted answers against the expected correct answers.
     * Applies business rules for XP distribution: Full XP for first-try correct answers,
     * and half XP for successfully retried answers.
     *
     * @param exercises The list of exercises in the lesson.
     * @param request   The user's submission payload.
     * @param mistakes  A list to be populated with the user's initial mistakes for the summary screen.
     * @return EvaluationDetails containing the correct answer count and the dynamically calculated XP.
     */
    private EvaluationDetails calculateEvaluationDetails(List<Exercise> exercises, LessonSubmitRequest request, List<MistakeDTO> mistakes) {
        int correctCount = 0;
        int potentialXp = 0;

        // Group submissions by exercise ID to handle potential multiple attempts (initial + retry)
        Map<UUID, List<ExerciseSubmission>> subsByExercise = request.getAnswers().stream()
                .collect(Collectors.groupingBy(ExerciseSubmission::getExerciseId));

        for (Exercise exercise : exercises) {
            // Only evaluate if the backend has a definitive correct answer for this exercise
            if (exercise.getCorrectAnswer() != null && exercise.getCorrectAnswer().containsKey("answer")) {
                String rawExpected = String.valueOf(exercise.getCorrectAnswer().get("answer"));
                String cleanExpected = normalizeText(rawExpected);

                List<ExerciseSubmission> subs = subsByExercise.getOrDefault(exercise.getExerciseId(), new ArrayList<>());

                // Separate the first attempt from a potential retry attempt based on the frontend flag
                ExerciseSubmission firstAttempt = subs.stream().filter(s -> !s.isRetry()).findFirst().orElse(null);
                ExerciseSubmission retryAttempt = subs.stream().filter(ExerciseSubmission::isRetry).findFirst().orElse(null);

                String firstAnswer = (firstAttempt != null && firstAttempt.getAnswer() != null) ? firstAttempt.getAnswer().toString() : "";
                boolean firstCorrect = cleanExpected.equals(normalizeText(firstAnswer));

                if (firstCorrect) {
                    // CASE 1: Flawless execution. Award a point and full XP.
                    correctCount++;
                    potentialXp += XP_PER_CORRECT_ANSWER; // +10 XP
                } else {
                    // Log the mistake for the frontend Summary Screen using the initial wrong answer
                    String questionText = exercise.getContent() != null && exercise.getContent().containsKey("question")
                            ? String.valueOf(exercise.getContent().get("question"))
                            : "Unknown question";
                    mistakes.add(new MistakeDTO(questionText, firstAnswer, rawExpected));

                    // CASE 2: The user failed initially, but we check if they fixed it via the retry queue
                    if (retryAttempt != null) {
                        String retryAnswer = retryAttempt.getAnswer() != null ? retryAttempt.getAnswer().toString() : "";
                        boolean retryCorrect = cleanExpected.equals(normalizeText(retryAnswer));

                        if (retryCorrect) {
                            // The user learned from the mistake. Count it towards passing the lesson.
                            correctCount++;
                            // Apply penalty: award only half XP for requiring a second attempt.
                            potentialXp += (XP_PER_CORRECT_ANSWER / 2); // +5 XP
                        }
                    }
                }
            }
        }
        return new EvaluationDetails(correctCount, potentialXp);
    }

    /**
     * Internal Data Transfer Object (DTO) to hold the results of the complex
     * answer evaluation process, including the penalty-adjusted XP.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class EvaluationDetails {
        private int correctCount;
        private int potentialXp;
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
                .replaceAll("\\p{Punct}", "")
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Transactional(readOnly = true)
    public ExerciseCheckResponse checkSingleExercise(UUID exerciseId, String userAnswer) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise not found"));

        if (exercise.getCorrectAnswer() == null || !exercise.getCorrectAnswer().containsKey("answer")) {
            return ExerciseCheckResponse.builder().isCorrect(true).build();
        }

        String rawExpected = String.valueOf(exercise.getCorrectAnswer().get("answer"));
        String normExpected = normalizeText(rawExpected);
        String normUser = normalizeText(userAnswer);

        // Perfect match
        if (normExpected.equals(normUser)) {
            return ExerciseCheckResponse.builder()
                    .isCorrect(true).isAlmostCorrect(false)
                    .feedbackMessage("Tökéletes! ✅").build();
        }

        // Almost correct check
        boolean isAlmostCorrect = false;
        String feedbackMsg = "Helytelen! Semmi baj, menjünk tovább. ❌";

        if ("WORD_BANK".equals(exercise.getType())) {
            // WORDBANK: only 1 item in the wrong place?
            List<String> expectedWords = List.of(normExpected.split(" "));
            List<String> userWords = List.of(normUser.split(" "));

            if (expectedWords.size() == userWords.size() && new HashSet<>(expectedWords).containsAll(userWords)) {
                int misplaced = 0;
                for (int i = 0; i < expectedWords.size(); i++) {
                    if (!expectedWords.get(i).equals(userWords.get(i))) misplaced++;
                }
                if (misplaced > 0 && misplaced <= 2) {
                    isAlmostCorrect = true;
                    feedbackMsg = "Majdnem jó! Csak a szórendet keverted meg egy kicsit. 🔄";
                }
            }
        } else {
            // TYPING: Levenshtein distance
            int distance = calculateLevenshteinDistance(normExpected, normUser);
            if (distance <= 2 && normExpected.length() > 4) {
                isAlmostCorrect = true;
                feedbackMsg = "Majdnem jó! Csak valahol elírtad egy kicsit. ✍️";
            }
        }

        // Build response
        return ExerciseCheckResponse.builder()
                .isCorrect(false)
                .isAlmostCorrect(isAlmostCorrect)
                //.correctAnswer(null)
                .feedbackMessage(feedbackMsg)
                .build();
    }

    // Levenshtein algorithm
    private int calculateLevenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) dp[i][j] = j;
                else if (j == 0) dp[i][j] = i;
                else dp[i][j] = min(dp[i - 1][j - 1] +
                            costOfSubstitution(a.charAt(i - 1), b.charAt(j - 1)),
                            dp[i - 1][j] + 1, dp[i][j - 1] + 1);
            }
        }
        return dp[a.length()][b.length()];
    }

    private int costOfSubstitution(char a, char b) { return a == b ? 0 : 1; }
    private int min(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }

}