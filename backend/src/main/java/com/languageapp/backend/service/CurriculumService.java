package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ExerciseImportRequest;
import com.languageapp.backend.dto.request.LessonImportRequest;
import com.languageapp.backend.dto.request.TopicImportRequest;
import com.languageapp.backend.entity.Exercise;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.LessonTopic;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.ExerciseRepository;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.LessonTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service responsible for managing curriculum data (Topics, Lessons, Exercises).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurriculumService {

    private final LessonTopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final ExerciseRepository exerciseRepository;

    /**
     * Imports a full curriculum structure (Topic -> Lessons -> Exercises) from a JSON request.
     */
    @Transactional
    public void importTopicAndLessons(TopicImportRequest request) {
        log.info("Starting curriculum import for topic: {}", request.getTopicName());

        LessonTopic topic = new LessonTopic();
        topic.setName(request.getTopicName());
        topic.setDescription(request.getDescription());

        LessonTopic savedTopic = topicRepository.save(topic);
        log.info("Saved Topic with ID: {}", savedTopic.getTopicId());

        if (request.getLessons() != null) {
            importLessonsInternal(savedTopic, request.getLessons());
        }
        log.info("Curriculum import completed successfully.");
    }

    /**
     * Imports new Lessons (and their Exercises) into an EXISTING Topic.
     */
    @Transactional
    public void importLessonsToExistingTopic(UUID topicId, List<LessonImportRequest> lessonRequests) {
        log.info("Importing {} new lessons to existing topic ID: {}", lessonRequests.size(), topicId);

        LessonTopic existingTopic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic nem található az importáláshoz!"));

        importLessonsInternal(existingTopic, lessonRequests);
    }

    /**
     * Imports new Exercises into an EXISTING Lesson.
     */
    @Transactional
    public void importExercisesToExistingLesson(UUID lessonId, List<ExerciseImportRequest> exerciseRequests) {
        log.info("Importing {} new exercises to existing lesson ID: {}", exerciseRequests.size(), lessonId);

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lecke nem található az importáláshoz!"));

        importExercisesInternal(lesson, exerciseRequests);
    }

    // --- Private Helper Methods for Reusability ---

    private void importLessonsInternal(LessonTopic parentTopic, List<LessonImportRequest> lessonRequests) {
        for (LessonImportRequest lessonReq : lessonRequests) {
            Lesson lesson = new Lesson();
            lesson.setTopic(parentTopic);
            lesson.setTitle(lessonReq.getTitle());
            lesson.setDifficulty(lessonReq.getDifficulty());
            lesson.setDescription(lessonReq.getDescription());
            lesson.setLanguage(lessonReq.getLanguage());

            Lesson savedLesson = lessonRepository.save(lesson);
            log.info("  Saved Lesson: {} ({})", savedLesson.getTitle(), savedLesson.getDifficulty());

            if (lessonReq.getExercises() != null) {
                importExercisesInternal(savedLesson, lessonReq.getExercises());
            }
        }
    }

    private void importExercisesInternal(Lesson parentLesson, List<ExerciseImportRequest> exerciseRequests) {
        for (ExerciseImportRequest exerciseReq : exerciseRequests) {
            Exercise exercise = new Exercise();
            exercise.setLesson(parentLesson);
            exercise.setType(exerciseReq.getType());
            exercise.setContent(exerciseReq.getContent());
            exercise.setCorrectAnswer(exerciseReq.getCorrectAnswer());

            if (exerciseReq.getImageUrl() != null) {
                exercise.setImageUrl(exerciseReq.getImageUrl());
            }

            exerciseRepository.save(exercise);
        }
        log.info("    Saved {} exercises for lesson.", exerciseRequests.size());
    }
}