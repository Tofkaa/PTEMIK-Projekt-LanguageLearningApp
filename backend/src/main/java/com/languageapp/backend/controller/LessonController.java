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
    public ResponseEntity<List<LessonResponse>> getAllLessons(Authentication authentication) {
        log.info("REST request to get all lessons for authenticated user");

        String userEmail = authentication.getName();

        return ResponseEntity.ok(lessonService.getAllLessonsForUser(userEmail));
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
        log.info("REST request to fetch exercises for lesson ID: {}", id);
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

        log.info("REST request to submit answers for lesson ID: {}", id);
        String userEmail = authentication.getName();
        UUID userId = userService.getUserProfile(userEmail).getUserId();

        LessonSubmitResponse response = evaluationService.evaluateLesson(userId, id, challengeId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves basic metadata for all lessons regardless of difficulty.
     * Safe endpoint designed specifically for populating the Challenge creation dropdown menu.
     *
     * @return A list of all available lessons.
     */
    @GetMapping("/all-for-challenge")
    public ResponseEntity<List<LessonResponse>> getAllLessonsForChallenge() {
        log.info("REST request to get all lessons for challenge dropdown");
        return ResponseEntity.ok(lessonService.getAllLessonsForChallengeDropdown());
    }
}