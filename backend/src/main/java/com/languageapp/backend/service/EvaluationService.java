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
import com.languageapp.backend.validation.AnswerLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import java.time.Clock;

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
    private final Clock clock;

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
    private final ChallengeRepository challengeRepository;
    private final ChallengeService challengeService;
    private final AssignmentSessionRepository sessionRepository;
    private final StreakService streakService;
    private final LessonAttemptRepository attemptRepository;
    private final MasteryService masteryService;

    /**
     * Orchestrates the entire submission process for a completed lesson.
     * Validates security, calculates scores and XP, updates the Daily Streak, and saves the final result.
     *
     * @param userId   The ID of the user submitting the lesson.
     * @param lessonId The ID of the lesson being submitted.
     * @param challengeId Optional challenge ID.
     * @param request  The payload containing time taken and all answers.
     * @return LessonSubmitResponse containing the evaluation results for the frontend.
     */
    @Transactional
    public LessonSubmitResponse evaluateLesson(UUID userId, UUID lessonId, UUID challengeId, LessonSubmitRequest request) {
        AnswerLimits.requireSubmissions(request.getAnswers(), AnswerLimits.MAX_LESSON_SUBMISSIONS);
        log.debug("lesson.evaluation.started");

        User user = userRepository.findForLearningUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.getAttemptId() == null) throw new ForbiddenException("Start a lesson attempt before submitting");
        LessonAttempt attempt = attemptRepository.findForSubmission(request.getAttemptId())
                .orElseThrow(() -> new ResourceNotFoundException("Lesson attempt not found"));
        if (!attempt.getUser().getUserId().equals(userId) || !attempt.getLesson().getLessonId().equals(lessonId)
                || !Objects.equals(attempt.getChallengeId(), challengeId))
            throw new ForbiddenException("This attempt belongs to another user, lesson or challenge");
        if (attempt.getSubmissionResult() != null) return attempt.getSubmissionResult();

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found"));

        Challenge challenge = null;
        String currentAttemptDifficulty;

        // --- SECURE CHALLENGE BYPASS LOGIC & DIFFICULTY DETERMINATION ---
        if (challengeId != null) {
            if (!user.isVerified()) throw new ForbiddenException("Kihíváshoz megerősített e-mail cím szükséges.");
            challenge = challengeRepository.findById(challengeId)
                    .orElseThrow(() -> new ForbiddenException("Security error: The specified challenge does not exist!"));

            boolean isChallenger = challenge.getChallenger().getUserId().equals(userId);
            boolean isOpponent = challenge.getOpponent().getUserId().equals(userId);

            if (!isChallenger && !isOpponent) {
                log.warn("challenge.submission.denied");
                throw new ForbiddenException("Security error: You are not a participant in this challenge!");
            }

            if (!challenge.getLesson().getLessonId().equals(lessonId)) {
                log.warn("lesson.submission.denied");
                throw new ForbiddenException("Security error: This challenge does not belong to the requested lesson!");
            }

            log.info("challenge.difficulty.bound");
            currentAttemptDifficulty = attempt.getDifficulty();
        } else {
            // Standard gameplay: Use current adaptive difficulty
            currentAttemptDifficulty = attempt.getDifficulty();
        }

        // Fetch progress specifically mapped to the current difficulty level
        var existingProgressOpt = progressRepository.findByUserUserIdAndLessonLessonIdAndCompletedDifficulty(userId, lessonId, currentAttemptDifficulty);

        // Filter the exercises that were actually evaluated in this session
        List<Exercise> activeExercises = attempt.getExerciseSnapshot().stream()
                .map(LessonAttempt.AllocatedExercise::asExercise).toList();

        if (activeExercises.size() != attempt.getExerciseIds().size())
            throw new com.languageapp.backend.exception.ConflictException("A feladatsor megváltozott. Indítsd újra a leckét.");
        Set<UUID> submittedIds = new HashSet<>();
        Set<UUID> retriedIds = new HashSet<>();
        for (ExerciseSubmission answer : request.getAnswers()) {
            if (!attempt.getExerciseIds().contains(answer.getExerciseId()))
                throw new ForbiddenException("Answer outside the allocated lesson");
            if (!answer.isRetry() && !submittedIds.add(answer.getExerciseId()))
                throw new ForbiddenException("Duplicate initial answer");
            if (answer.isRetry() && !retriedIds.add(answer.getExerciseId()))
                throw new ForbiddenException("Duplicate retry answer");
        }
        if (submittedIds.size() != activeExercises.size()) throw new ForbiddenException("Missing allocated answers");

        int totalQuestions = activeExercises.size();
        List<MistakeDTO> mistakes = new ArrayList<>();

        // Calculate the score and dynamic XP (accounting for retries)
        EvaluationDetails details = calculateEvaluationDetails(activeExercises, request, mistakes);
        int correctAnswersCount = details.getCorrectCount();
        int potentialXp = details.getPotentialXp();

        int score = totalQuestions == 0 ? 0 : (int) Math.round(((double) correctAnswersCount / totalQuestions) * 100);
        boolean passed = score >= PASSING_SCORE_THRESHOLD;

        // Utilize the previously fetched Progress, or initialize a new one if this is the first attempt
        Progress progress = existingProgressOpt.orElseGet(() -> {
            Progress newProgress = new Progress();
            newProgress.setUser(user);
            newProgress.setLesson(lesson);
            newProgress.setCompletedDifficulty(currentAttemptDifficulty); // NEW: Track difficulty level in progress
            newProgress.setHighestScore(0);
            newProgress.setIsCompleted(false);
            return newProgress;
        });

        int xpEarned = 0;

        // --- 1. XP ALLOCATION LOGIC ---
        // Only award XP if the user passed and hadn't already completed this lesson (at this difficulty) in the past
        if (passed && !progress.getIsCompleted()) {
            xpEarned = potentialXp;
            user.setXp(user.getXp() + xpEarned);
            log.info("lesson.xp.awarded");
        } else if (passed && progress.getIsCompleted()) {
            log.debug("lesson.xp.unchanged");
        }

        // --- 2. GAMIFICATION: DAILY STREAK ENGINE ---
        if (passed) {
            streakService.updateActivity(user);
        }

        // --- 3. DYNAMIC DIFFICULTY EVALUATION ---
        String oldDifficulty = currentAttemptDifficulty;
        String newDifficulty = currentAttemptDifficulty;
        boolean isLevelUp = false;
        boolean isLevelDown = false;

        
        if (challenge == null && !attempt.isManualPractice()) {
            oldDifficulty = userDifficultyCalculator.determineTargetDifficulty(user, lesson.getTopic().getCourse().getCourseId());
        }


        Result savedResult = saveResult(user, lesson, request, correctAnswersCount, totalQuestions, score, challenge, currentAttemptDifficulty);
        savedResult.setManualPractice(attempt.isManualPractice());
        updateProgress(progress, score, passed);


        if (challenge == null && !attempt.isManualPractice()) {
            newDifficulty = userDifficultyCalculator.determineTargetDifficulty(user, lesson.getTopic().getCourse().getCourseId());
            isLevelUp = checkLevelUp(oldDifficulty, newDifficulty);
            isLevelDown = checkLevelDown(oldDifficulty, newDifficulty);
        }

        // --- THE EVALUATION ENGINE TRIGGER ---
        if (challengeId != null) {
            challengeService.processChallengeResult(challengeId, userId, score, request.getTimeTakenSeconds());
        }

        String feedback = passed ? "Gratulálunk, sikeresen teljesítetted a leckét!" : "Gyakorolj tovább, menni fog!";
        var newAchievements = achievementService.checkAndAwardAchievements(user, score);

        var userProgress = progressRepository.findByUserUserId(userId);
        var courseLessons = lessonRepository.findByTopic_Course_CourseId(lesson.getTopic().getCourse().getCourseId());
        LessonSubmitResponse response = LessonSubmitResponse.builder()
                .newAchievements(newAchievements)
                .resultId(savedResult.getResultId())
                .score(score)
                .correctAnswersCount(correctAnswersCount)
                .totalQuestionsCount(totalQuestions)
                .xpEarned(xpEarned)
                .passed(passed)
                .feedback(feedback)
                .mistakes(mistakes)
                .newStreak(user.getStreak() != null ? user.getStreak() : 0)
                .newDifficulty(newDifficulty)
                .isLevelUp(isLevelUp)
                .isLevelDown(isLevelDown)
                .manualPractice(attempt.isManualPractice())
                .lessonMastered(masteryService.isMastered(lesson, userProgress))
                .topicMastered(masteryService.isTopicMastered(lesson.getTopic().getTopicId(), courseLessons, userProgress))
                .build();
        attempt.setSubmissionResult(response);
        attemptRepository.save(attempt);
        return response;
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
    public EvaluationDetails calculateEvaluationDetails(List<Exercise> exercises, LessonSubmitRequest request, List<MistakeDTO> mistakes) {
        AnswerLimits.requireSubmissions(request.getAnswers(), AnswerLimits.MAX_LESSON_SUBMISSIONS);
        int correctCount = 0;
        int potentialXp = 0;

        // Group submissions by exercise ID to handle potential multiple attempts (initial + retry)
        Map<UUID, List<ExerciseSubmission>> subsByExercise = request.getAnswers().stream()
                .collect(Collectors.groupingBy(ExerciseSubmission::getExerciseId));

        for (Exercise exercise : exercises) {
            String rawExpected = expectedAnswer(exercise);
            String cleanExpected = normalizeText(rawExpected);
            List<ExerciseSubmission> subs = subsByExercise.getOrDefault(exercise.getExerciseId(), List.of());
            ExerciseSubmission first = subs.stream().filter(s -> !s.isRetry()).findFirst().orElse(null);
            ExerciseSubmission retry = subs.stream().filter(ExerciseSubmission::isRetry).findFirst().orElse(null);
            String firstAnswer = first == null ? "" : (String) first.getAnswer();
            if (cleanExpected.equals(normalizeText(firstAnswer))) {
                correctCount++;
                potentialXp += XP_PER_CORRECT_ANSWER;
            } else {
                String question = exercise.getContent() != null && exercise.getContent().containsKey("question")
                        ? String.valueOf(exercise.getContent().get("question")) : "Unknown question";
                mistakes.add(new MistakeDTO(exercise.getExerciseId(),question,firstAnswer,rawExpected));
                if (retry != null && cleanExpected.equals(normalizeText((String)retry.getAnswer()))) {
                    correctCount++;
                    potentialXp += XP_PER_CORRECT_ANSWER / 2;
                }
            }
        }
        return new EvaluationDetails(correctCount, potentialXp);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class EvaluationDetails {
        private int correctCount;
        private int potentialXp;
    }

    private Result saveResult(User user, Lesson lesson, LessonSubmitRequest request,
                              int correctCount, int totalCount, int score, Challenge challenge, String difficulty) {
        Result result = new Result();
        result.setUser(user);
        result.setLesson(lesson);
        result.setDifficulty(difficulty);
        result.setScore(score);
        result.setTimeTaken(request.getTimeTakenSeconds());
        result.setSubmittedAt(clock.instant());
        result.setCorrectAnswersCount(correctCount);
        result.setTotalQuestionsCount(totalCount);
        result.setIsTestResult(false);
        result.setIsChallengeResult(challenge != null);
        result.setChallenge(challenge);

        return resultRepository.save(result);
    }

    private int getDifficultyRank(String difficulty) {
        if (difficulty == null) return 0;
        return switch (difficulty) {
            case "EASY" -> 1;
            case "MEDIUM" -> 2;
            case "HARD" -> 3;
            default -> 0;
        };
    }

    private boolean checkLevelUp(String oldDifficulty, String newDifficulty) {
        int oldRank = getDifficultyRank(oldDifficulty);
        int newRank = getDifficultyRank(newDifficulty);
        return oldRank != 0 && newRank > oldRank;
    }

    private boolean checkLevelDown(String oldDifficulty, String newDifficulty) {
        int oldRank = getDifficultyRank(oldDifficulty);
        int newRank = getDifficultyRank(newDifficulty);
        return oldRank != 0 && newRank < oldRank;
    }

    private void updateProgress(Progress progress, int score, boolean passed) {
        progress.setLastAttemptAt(clock.instant());

        if (score > progress.getHighestScore()) {
            progress.setHighestScore(score);
        }

        if (passed && !progress.getIsCompleted()) {
            progress.setIsCompleted(true);
            progress.setCompletedAt(clock.instant());
        }

        progressRepository.save(progress);
    }

    private String normalizeText(String input) {
        return com.languageapp.backend.validation.AnswerText.normalize(input);
    }

    @Transactional(readOnly = true)
    public ExerciseCheckResponse checkSingleExercise(UUID exerciseId, String userAnswer) {
        AnswerLimits.requireText(userAnswer);
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResourceNotFoundException("Exercise not found"));
        return checkExerciseAnswer(exercise, userAnswer);
    }

    @Transactional(readOnly = true)
    public ExerciseCheckResponse checkAttemptExercise(UUID lessonId, UUID attemptId, UUID exerciseId, String email, String answer) {
        AnswerLimits.requireText(answer);
        LessonAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson attempt not found"));
        if (!attempt.getUser().getEmail().equals(email) || !attempt.getLesson().getLessonId().equals(lessonId))
            throw new ForbiddenException("This attempt belongs to another user or lesson");
        if (attempt.getChallengeId() != null && !attempt.getUser().isVerified())
            throw new ForbiddenException("Kihíváshoz megerősített e-mail cím szükséges.");
        Exercise exercise = attempt.getExerciseSnapshot().stream().filter(e -> e.exerciseId().equals(exerciseId))
                .findFirst().orElseThrow(() -> new ForbiddenException("Exercise outside the allocated lesson")).asExercise();
        return checkExerciseAnswer(exercise, answer);
    }

    private ExerciseCheckResponse checkExerciseAnswer(Exercise exercise, String userAnswer) {

        String rawExpected = expectedAnswer(exercise);
        String normExpected = normalizeText(rawExpected);
        String normUser = normalizeText(userAnswer);

        if (normExpected.equals(normUser)) {
            return ExerciseCheckResponse.builder()
                    .isCorrect(true).isAlmostCorrect(false)
                    .feedbackMessage("Helyes válasz!").build();
        }

        boolean isAlmostCorrect = false;
        String feedbackMsg = "Helytelen válasz. Folytasd a gyakorlást!";

        if ("WORD_BANK".equals(exercise.getType())) {
            List<String> expectedWords = List.of(normExpected.split(" "));
            List<String> userWords = List.of(normUser.split(" "));

            if (expectedWords.size() == userWords.size() && new HashSet<>(expectedWords).containsAll(userWords)) {
                int misplaced = 0;
                for (int i = 0; i < expectedWords.size(); i++) {
                    if (!expectedWords.get(i).equals(userWords.get(i))) misplaced++;
                }
                if (misplaced > 0 && misplaced <= 2) {
                    isAlmostCorrect = true;
                    feedbackMsg = "Majdnem jó! Ellenőrizd a szavak sorrendjét!";
                }
            }
        } else {
            if (normExpected.length() > 4 && TypoTolerance.withinTwoEdits(normExpected, normUser)) {
                isAlmostCorrect = true;
                feedbackMsg = "Majdnem jó! Ellenőrizd az elgépelést!";
            }
        }

        return ExerciseCheckResponse.builder()
                .isCorrect(false)
                .isAlmostCorrect(isAlmostCorrect)
                .feedbackMessage(feedbackMsg)
                .build();
    }

    private void requireExpectedAnswer(String answer) {
        if (answer.length() > AnswerLimits.MAX_CHARACTERS)
            throw new com.languageapp.backend.exception.ConflictException("A feladat megoldása túl hosszú az értékeléshez. Jelezd az oktatónak.");
    }
    private String expectedAnswer(Exercise exercise) {
        Object value = exercise.getCorrectAnswer() == null ? null : exercise.getCorrectAnswer().get("answer");
        if (!(value instanceof String text))
            throw new com.languageapp.backend.exception.ConflictException("A feladat mintamegoldása hiányzik vagy érvénytelen. Jelezd az oktatónak.");
        requireExpectedAnswer(text);
        if (normalizeText(text).isEmpty())
            throw new com.languageapp.backend.exception.ConflictException("A feladat mintamegoldása hiányzik vagy érvénytelen. Jelezd az oktatónak.");
        return text;
    }
}
