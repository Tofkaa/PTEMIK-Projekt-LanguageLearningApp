package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.ExerciseResponse;
import com.languageapp.backend.dto.response.LessonResponse;
import com.languageapp.backend.entity.Exercise;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service responsible for managing and retrieving educational content.
 * <p>
 * Ensures that sensitive data (like correct answers) is stripped by mapping
 * entities to safe Data Transfer Objects (DTOs) before returning them to the controller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;

    /**
     * Retrieves a list of all available lessons mapped to safe DTOs.
     *
     * @return a list of {@link LessonResponse}
     */
    @Transactional(readOnly = true) // Optimalizálja az olvasási műveletet a Hibernate-ben
    public List<LessonResponse> getAllLessons() {
        log.debug("Fetching all available lessons from the database.");

        return lessonRepository.findAll().stream()
                .map(this::mapToLessonResponse)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all exercises for a specific lesson, strictly omitting the correct answers.
     *
     * @param lessonId the unique identifier of the lesson
     * @return a list of {@link ExerciseResponse}
     * @throws ResourceNotFoundException if the requested lesson does not exist
     */
    @Transactional(readOnly = true)
    public List<ExerciseResponse> getExercisesByLessonId(UUID lessonId) {
        log.debug("Fetching safe exercises for lesson ID: {}", lessonId);

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> {
                    log.warn("Fetch failed: Lesson not found with ID {}", lessonId);
                    return new ResourceNotFoundException("No lesson found with this ID!");
                });

        return lesson.getExercises().stream()
                .map(this::mapToExerciseResponse)
                .collect(Collectors.toList());
    }

    private LessonResponse mapToLessonResponse(Lesson lesson) {
        return new LessonResponse(
                lesson.getLessonId(),
                lesson.getTopic().getName(),
                lesson.getTitle(),
                lesson.getDifficulty(),
                lesson.getLanguage(),
                lesson.getDescription()
        );
    }

    private ExerciseResponse mapToExerciseResponse(Exercise exercise) {
        return new ExerciseResponse(
                exercise.getExerciseId(),
                exercise.getLesson().getLessonId(),
                exercise.getType(),
                exercise.getContent(),
                exercise.getAudioUrl(),
                exercise.getImageUrl()
        );
    }
}