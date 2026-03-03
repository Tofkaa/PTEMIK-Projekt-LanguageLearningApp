package com.languageapp.backend.controller;

import com.languageapp.backend.dto.response.ExerciseResponse;
import com.languageapp.backend.dto.response.LessonResponse;
import com.languageapp.backend.service.LessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

    /**
     * Retrieves a summary list of all available lessons.
     *
     * @return a {@link ResponseEntity} containing a list of {@link LessonResponse}
     */
    @GetMapping
    public ResponseEntity<List<LessonResponse>> getAllLessons() {
        log.info("REST request to fetch all lessons.");
        return ResponseEntity.ok(lessonService.getAllLessons());
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
}