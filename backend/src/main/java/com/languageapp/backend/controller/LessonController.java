package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.ExerciseCheckRequest;
import com.languageapp.backend.dto.request.LessonSubmitRequest;
import com.languageapp.backend.dto.response.ExerciseCheckResponse;
import com.languageapp.backend.dto.response.ExerciseResponse;
import com.languageapp.backend.dto.response.LessonResponse;
import com.languageapp.backend.dto.response.LessonSubmitResponse;
import com.languageapp.backend.service.EvaluationService;
import com.languageapp.backend.service.LessonService;
import com.languageapp.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for retrieving learning materials.
 * <p>
 * Exposes endpoints for the frontend to fetch available lessons
 * and their corresponding interactive exercises.
 */
@Slf4j
@RestController
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
public class LessonController {

    private final LessonService lessonService;
    private final EvaluationService evaluationService;
    private final UserService userService;

    /**
     * Retrieves a list of available lessons tailored to the authenticated user's difficulty level.
     *
     * @param authentication the current authenticated user's security context
     * @return a {@link ResponseEntity} containing a list of {@link LessonResponse}
     */
    @GetMapping
    public ResponseEntity<List<LessonResponse>> getAllLessons(Authentication authentication, @RequestParam(required=false) UUID courseId) {
        log.debug("REST request to get all lessons for authenticated user");

        String userEmail = authentication.getName();

        return ResponseEntity.ok(lessonService.getAllLessonsForUser(userEmail, courseId));
    }

    /**
     * Retrieves all safe exercises (without answers) for a specific lesson.
     * Includes an optional challengeId to bypass standard difficulty restrictions if the user is in a duel.
     *
     * @param id the UUID of the requested lesson
     * @param challengeId (Optional) The UUID of the active challenge.
     * @param authentication the current authenticated user's security context
     * @return a {@link ResponseEntity} containing a list of {@link ExerciseResponse}
     */
    @GetMapping("/{id}/exercises")
    public ResponseEntity<List<ExerciseResponse>> getExercisesByLesson(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID challengeId,
            Authentication authentication) {
        log.info("lesson.exercises.requested");
        return ResponseEntity.ok(lessonService.getExercisesByLessonId(id, authentication.getName(), challengeId));
    }

    /**
     * Submits a completed lesson for evaluation.
     *
     * @param id the UUID of the lesson
     * @param challengeId (Optional) The UUID of the active challenge, used to trigger the Challenge Engine.
     * @param request the submission payload
     * @param authentication the current authenticated user's security context
     * @return a {@link ResponseEntity} containing the evaluation results
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<LessonSubmitResponse> submitLesson(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID challengeId,
            @Valid @RequestBody LessonSubmitRequest request,
            Authentication authentication) {

        log.info("lesson.submit.requested");
        String userEmail = authentication.getName();
        UUID userId = userService.getUserProfile(userEmail).getUserId();

        LessonSubmitResponse response = evaluationService.evaluateLesson(userId, id, challengeId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/attempts")
    public ResponseEntity<com.languageapp.backend.dto.response.LessonStartResponse> startAttempt(
            @PathVariable UUID id, @RequestParam(required = false) UUID challengeId,
            @RequestParam(required = false) String difficulty, Authentication authentication) {
        return ResponseEntity.ok(lessonService.startAttempt(id, authentication.getName(), challengeId, difficulty));
    }

    @PostMapping("/{id}/attempts/{attemptId}/exercises/{exerciseId}/check")
    public ResponseEntity<ExerciseCheckResponse> checkAttemptExercise(@PathVariable UUID id,
            @PathVariable UUID attemptId, @PathVariable UUID exerciseId,
            @Valid @RequestBody ExerciseCheckRequest request, Authentication authentication) {
        return ResponseEntity.ok(evaluationService.checkAttemptExercise(id, attemptId, exerciseId,
                authentication.getName(), request.getAnswer()));
    }

    /**
     * Retrieves basic metadata for all lessons regardless of difficulty.
     * Safe endpoint designed specifically for populating the Challenge creation dropdown menu.
     *
     * @param courseCode (Optional) Specifies the language course for the dropdown. Defaults to user's active course if omitted.
     * @return A list of all available lessons.
     */
    @GetMapping("/all-for-challenge")
    public ResponseEntity<List<LessonResponse>> getAllLessonsForChallenge(
            @RequestParam(required = false) String courseCode,
            Authentication authentication) {
        log.info("lesson.catalog.requested");
        return ResponseEntity.ok(lessonService.getAllLessonsForChallengeDropdown(authentication.getName(), courseCode));
    }
}
