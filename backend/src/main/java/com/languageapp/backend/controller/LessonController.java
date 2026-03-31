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
     * @param fallbackLevel optional starting level for new dynamic users
     * @return a {@link ResponseEntity} containing a list of {@link LessonResponse}
     */
    @GetMapping
    public ResponseEntity<List<LessonResponse>> getAllLessons(
            Authentication authentication,
            @RequestParam(required = false) String fallbackLevel // <-- IDE JÖN AZ ANNOTÁCIÓ
    ) {
        log.info("REST request to get all lessons for user with fallback: {}", fallbackLevel);

        String userEmail = authentication.getName();

        // Továbbpasszoljuk a fallbackLevel-t a Service-nek
        return ResponseEntity.ok(lessonService.getAllLessonsForUser(authentication.getName(), fallbackLevel));
    }
    /**
     * Retrieves all safe exercises (without answers) for a specific lesson.
     *
     * @param id the UUID of the requested lesson
     * @return a {@link ResponseEntity} containing a list of {@link ExerciseResponse}
     */
    @GetMapping("/{id}/exercises")
    public ResponseEntity<List<ExerciseResponse>> getExercisesByLesson(
            @PathVariable UUID id,
            Authentication authentication,
            @RequestParam(required = false) String fallbackLevel // <-- 1. ÚJ PARAMÉTER
    ) {
        log.info("REST request to fetch exercises for lesson ID: {}", id);
        // 2. TOVÁBBPASSZOLJUK A SERVICE-NEK
        return ResponseEntity.ok(lessonService.getExercisesByLessonId(id, authentication.getName(), fallbackLevel));
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
            @RequestParam(required = false) String fallbackLevel, // <-- Vedd fel a paramétert!
            Authentication authentication) {

        log.info("REST request to submit answers for lesson ID: {}", id);

        String userEmail = authentication.getName();
        UUID userId = userService.getUserProfile(userEmail).getUserId();

        // Passzold tovább a Service-nek (ezt a metódust is frissítened kell az EvaluationService-ben)
        LessonSubmitResponse response = evaluationService.evaluateLesson(userId, id, request, fallbackLevel);

        return ResponseEntity.ok(response);
    }
}