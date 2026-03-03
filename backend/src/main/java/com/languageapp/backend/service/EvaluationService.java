package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ExerciseSubmission;
import com.languageapp.backend.dto.request.LessonSubmitRequest;
import com.languageapp.backend.dto.response.LessonSubmitResponse;
import com.languageapp.backend.entity.*;
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

    /**
     * Evaluates a submitted lesson, calculates the score, and updates user statistics.
     *
     * @param userId  the UUID of the authenticated user
     * @param lessonId the UUID of the lesson being evaluated
     * @param request the submission payload containing answers and time taken
     * @return a {@link LessonSubmitResponse} detailing the evaluation outcome
     */
    @Transactional
    public LessonSubmitResponse evaluateLesson(UUID userId, UUID lessonId, LessonSubmitRequest request) {
        log.debug("Starting evaluation for user: {} and lesson: {}", userId, lessonId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found"));

        List<Exercise> exercises = lesson.getExercises();
        int totalQuestions = exercises.size();
        int correctAnswersCount = calculateCorrectAnswers(exercises, request);

        int score = totalQuestions == 0 ? 0 : (int) Math.round(((double) correctAnswersCount / totalQuestions) * 100);
        boolean passed = score >= PASSING_SCORE_THRESHOLD;
        int xpEarned = passed ? (correctAnswersCount * XP_PER_CORRECT_ANSWER) : 0;

        if (xpEarned > 0) {
            user.setXp(user.getXp() + xpEarned);
            userRepository.save(user);
            log.info("User {} earned {} XP. Total XP: {}", user.getEmail(), xpEarned, user.getXp());
        }

        Result savedResult = saveResult(user, lesson, request, correctAnswersCount, totalQuestions, score);
        updateProgress(user, lesson, score, passed);

        String feedback = passed ? "Congratulations! You passed the lesson." : "Keep practicing! You can do better.";

        return LessonSubmitResponse.builder()
                .resultId(savedResult.getResultId())
                .score(score)
                .correctAnswersCount(correctAnswersCount)
                .totalQuestionsCount(totalQuestions)
                .xpEarned(xpEarned)
                .passed(passed)
                .feedback(feedback)
                .build();
    }

    private int calculateCorrectAnswers(List<Exercise> exercises, LessonSubmitRequest request) {
        Map<UUID, String> submittedAnswers = request.getAnswers().stream()
                .collect(Collectors.toMap(
                        ExerciseSubmission::getExerciseId,
                        submission -> String.valueOf(submission.getAnswer()).trim().toLowerCase()
                ));

        int correctCount = 0;
        for (Exercise exercise : exercises) {
            if (exercise.getCorrectAnswer() != null && exercise.getCorrectAnswer().containsKey("answer")) {
                String correctAnswer = String.valueOf(exercise.getCorrectAnswer().get("answer")).trim().toLowerCase();
                String submittedAnswer = submittedAnswers.get(exercise.getExerciseId());

                if (correctAnswer.equals(submittedAnswer)) {
                    correctCount++;
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

    private void updateProgress(User user, Lesson lesson, int score, boolean passed) {
        Progress progress = progressRepository.findByUserUserIdAndLessonLessonId(user.getUserId(), lesson.getLessonId())
                .orElseGet(() -> {
                    Progress newProgress = new Progress();
                    newProgress.setUser(user);
                    newProgress.setLesson(lesson);
                    newProgress.setHighestScore(0);
                    return newProgress;
                });

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
}