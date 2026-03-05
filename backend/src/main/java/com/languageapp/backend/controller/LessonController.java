package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.LessonSubmitRequest;
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
     *
     * @param id the UUID of the requested lesson
     * @return a {@link ResponseEntity} containing a list of {@link ExerciseResponse}
     */
    @GetMapping("/{id}/exercises")
    public ResponseEntity<List<ExerciseResponse>> getExercisesByLesson(@PathVariable UUID id) {
        log.info("REST request to fetch exercises for lesson ID: {}", id);
        return ResponseEntity.ok(lessonService.getExercisesByLessonId(id));
    }

    /**
     * Submits a completed lesson for evaluation.
     *
     * @param id the UUID of the lesson
     * @param request the submission payload
     * @param authentication the current authenticated user's security context
     * @return a {@link ResponseEntity} containing the evaluation results
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<LessonSubmitResponse> submitLesson(
            @PathVariable UUID id,
            @Valid @RequestBody LessonSubmitRequest request,
            Authentication authentication) {

        log.info("REST request to submit answers for lesson ID: {}", id);

        // Get user email from SecurityContext.
        String userEmail = authentication.getName();
        UUID userId = userService.getUserProfile(userEmail).getUserId();
        LessonSubmitResponse response = evaluationService.evaluateLesson(userId, id, request);

        return ResponseEntity.ok(response);
    }
}