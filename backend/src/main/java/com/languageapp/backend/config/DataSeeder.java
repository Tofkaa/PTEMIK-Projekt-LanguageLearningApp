package com.languageapp.backend.config;

import com.languageapp.backend.entity.Exercise;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.LessonTopic;
import com.languageapp.backend.repository.ExerciseRepository;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.LessonTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Automatically loads the DB with test data,
 * if it is completely empty.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final LessonTopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final ExerciseRepository exerciseRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // Run it only when the DB is empty.
        if (topicRepository.count() == 0) {
            log.info("The DataBase is empty, seeding in progress...");
            seedLearningMaterials();
            log.info("Initial learning material successfully loaded!");
        } else {
            log.info("Database not empty, seeding skipped.");
        }
    }

    private void seedLearningMaterials() {
        // 1. Add topic
        LessonTopic basicsTopic = new LessonTopic();
        basicsTopic.setName("Angol Alapok");
        basicsTopic.setDescription("Kezdő angol szókincs és egyszerű nyelvtan.");
        topicRepository.save(basicsTopic);

        // 2. Add lesson
        Lesson colorsLesson = new Lesson();
        colorsLesson.setTopic(basicsTopic);
        colorsLesson.setTitle("1. Lecke: Színek és Gyümölcsök");
        colorsLesson.setDifficulty("KÖNNYŰ");
        colorsLesson.setLanguage("en");
        colorsLesson.setDescription("Tanuljuk meg az alapvető színeket és pár gyümölcs nevét!");
        lessonRepository.save(colorsLesson);

        // 3. Add exercises using JSONB

        // 1. Exercise (Multiple Choice)
        Exercise ex1 = new Exercise();
        ex1.setLesson(colorsLesson);
        ex1.setType("MULTIPLE_CHOICE");
        ex1.setContent(Map.of(
                "question", "Hogy van angolul az Alma?",
                "options", List.of("Apple", "Banana", "Orange", "Grape")
        ));
        ex1.setCorrectAnswer(Map.of("answer", "Apple")); // This will be hidden in the DTO
        exerciseRepository.save(ex1);

        // 2. Exercise (Translation)
        Exercise ex2 = new Exercise();
        ex2.setLesson(colorsLesson);
        ex2.setType("TRANSLATION");
        ex2.setContent(Map.of(
                "question", "Fordítsd le: A nap sárga.",
                "hint", "A nap = The sun"
        ));
        ex2.setCorrectAnswer(Map.of("answer", "The sun is yellow."));
        exerciseRepository.save(ex2);

        // 3. Exercise (Image-based)
        Exercise ex3 = new Exercise();
        ex3.setLesson(colorsLesson);
        ex3.setType("IMAGE_CHOICE");
        ex3.setImageUrl("https://example.com/images/red-car.jpg"); // fake URL for testing
        ex3.setContent(Map.of(
                "question", "Milyen színű az autó a képen?",
                "options", List.of("Red", "Blue", "Green", "Black")
        ));
        ex3.setCorrectAnswer(Map.of("answer", "Red"));
        exerciseRepository.save(ex3);
    }
}