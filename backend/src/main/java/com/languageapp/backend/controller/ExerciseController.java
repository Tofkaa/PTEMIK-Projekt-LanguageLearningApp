package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.ExerciseCheckRequest;
import com.languageapp.backend.dto.response.ExerciseCheckResponse;
import com.languageapp.backend.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for handling individual exercise operations.
 * Separated from LessonController for better domain-driven design.
 */
@RestController
@RequestMapping("/api/exercises")
@RequiredArgsConstructor
public class ExerciseController {

    private final EvaluationService evaluationService;

    /**
     * Immediate feedback endpoint. Evaluates a single answer using Levenshtein distance
     * and WordBank logic without returning the correct answer beforehand.
     */
    @PostMapping("/{exerciseId}/check")
    public ResponseEntity<ExerciseCheckResponse> checkExercise(
            @PathVariable UUID exerciseId,
            @RequestBody ExerciseCheckRequest request) {

        return ResponseEntity.ok(evaluationService.checkSingleExercise(exerciseId, request.getAnswer()));
    }
}