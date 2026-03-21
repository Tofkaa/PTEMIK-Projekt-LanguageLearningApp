package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.ExerciseImportRequest;
import com.languageapp.backend.dto.request.LessonImportRequest;
import com.languageapp.backend.dto.request.TopicImportRequest;
import com.languageapp.backend.entity.Exercise;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.LessonTopic;
import com.languageapp.backend.repository.ExerciseRepository;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.LessonTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Transactional ensures that if any part fails, the entire import is rolled back to prevent orphaned data.
     * * @param request The nested DTO containing the curriculum data.
     */
    @Transactional
    public void importTopicAndLessons(TopicImportRequest request) {
        log.info("Starting curriculum import for topic: {}", request.getTopicName());

        // 1. Topic creation and saving
        LessonTopic topic = new LessonTopic();
        topic.setName(request.getTopicName());
        topic.setDescription(request.getDescription());

        LessonTopic savedTopic = topicRepository.save(topic);
        log.info("Saved Topic with ID: {}", savedTopic.getTopicId());

        // 2. Iterate through lessons
        if (request.getLessons() != null) {
            for (LessonImportRequest lessonReq : request.getLessons()) {
                Lesson lesson = new Lesson();
                lesson.setTopic(savedTopic); // Assign to topic
                lesson.setTitle(lessonReq.getTitle());
                lesson.setDifficulty(lessonReq.getDifficulty());
                lesson.setDescription(lessonReq.getDescription());
                lesson.setLanguage(lessonReq.getLanguage());

                Lesson savedLesson = lessonRepository.save(lesson);
                log.info("  Saved Lesson: {} ({})", savedLesson.getTitle(), savedLesson.getDifficulty());

                // 3. Iterate through exercises
                if (lessonReq.getExercises() != null) {
                    for (ExerciseImportRequest exerciseReq : lessonReq.getExercises()) {
                        Exercise exercise = new Exercise();
                        exercise.setLesson(savedLesson); // Assign to lesson
                        exercise.setType(exerciseReq.getType());

                        exercise.setContent(exerciseReq.getContent());
                        exercise.setCorrectAnswer(exerciseReq.getCorrectAnswer());

                        if (exerciseReq.getImageUrl() != null) {
                            exercise.setImageUrl(exerciseReq.getImageUrl());
                        }

                        exerciseRepository.save(exercise);
                    }
                    log.info("    Saved {} exercises for lesson.", lessonReq.getExercises().size());
                }
            }
        }

        log.info("Curriculum import completed successfully.");
    }
}