package com.languageapp.backend.config;

import com.languageapp.backend.entity.Exercise;
import com.languageapp.backend.entity.Lesson;
import com.languageapp.backend.entity.LessonTopic;
import com.languageapp.backend.repository.ExerciseRepository;
import com.languageapp.backend.repository.LessonRepository;
import com.languageapp.backend.repository.LessonTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Component responsible for bootstrapping the database with initial learning materials.
 * <p>
 * Populates the system with topics, lessons, and exercises categorized by
 * different difficulty levels (EASY, MEDIUM, HARD) to support the adaptive learning engine.
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
    public void run(String @NonNull ... args) {
        // Run it only when the DB is empty.
        if (topicRepository.count() == 0) {
            log.info("Database is empty. Initializing structured seed data...");
            seedLearningMaterials();
            log.info("Seed data initialization completed successfully.");
        } else {
            log.info("Database already contains learning materials. Seeding skipped.");
        }
    }

    private void seedLearningMaterials() {
        // Add topic
        LessonTopic basicsTopic = new LessonTopic();
        basicsTopic.setName("Basic English");
        basicsTopic.setDescription("Beginner vocabulary and grammar essential for daily communication.");
        topicRepository.save(basicsTopic);

        // EASY
        Lesson easyLesson = createLesson(basicsTopic, "Colors & Fruits (EASY)", "EASY",
                "Learn basic vocabulary with visual aids and multiple choice questions.");

        createExercise(easyLesson, "IMAGE_CHOICE",
                Map.of("question", "What color is the apple?", "options", List.of("Red", "Blue", "Green")),
                Map.of("answer", "Red"), "https://example.com/red-apple.jpg");

        createExercise(easyLesson, "MULTIPLE_CHOICE",
                Map.of("question", "How do you say 'Banán' in English?", "options", List.of("Apple", "Banana", "Orange")),
                Map.of("answer", "Banana"), null);

        // MEDIUM
        Lesson mediumLesson = createLesson(basicsTopic, "Colors & Fruits (MEDIUM)", "MEDIUM",
                "Practice your vocabulary with translations and subtle hints.");

        createExercise(mediumLesson, "MULTIPLE_CHOICE",
                Map.of("question", "Which of the following fruits is yellow?", "options", List.of("Apple", "Banana", "Grape")),
                Map.of("answer", "Banana"), null);

        createExercise(mediumLesson, "TRANSLATION",
                Map.of("question", "Translate to English: Az alma piros.", "hint", "alma = apple, piros = red"),
                Map.of("answer", "The apple is red."), null);

        // HARD
        Lesson hardLesson = createLesson(basicsTopic, "Colors & Fruits (HARD)", "HARD",
                "Advanced translation practice without any hints or visual aids.");

        createExercise(hardLesson, "TRANSLATION",
                Map.of("question", "Translate to English: A nap sárga és az ég kék."),
                Map.of("answer", "The sun is yellow and the sky is blue."), null);

        createExercise(hardLesson, "TRANSLATION",
                Map.of("question", "Translate to English: Szeretem a zöld almát."),
                Map.of("answer", "I like the green apple."), null);
    }

    // Helper factory methods

    private Lesson createLesson(LessonTopic topic, String title, String difficulty, String description) {
        Lesson lesson = new Lesson();
        lesson.setTopic(topic);
        lesson.setTitle(title);
        lesson.setDifficulty(difficulty);
        lesson.setLanguage("en");
        lesson.setDescription(description);
        return lessonRepository.save(lesson);
    }

    private void createExercise(Lesson lesson, String type, Map<String, Object> content, Map<String, Object> correctAnswer, String imageUrl) {
        Exercise exercise = new Exercise();
        exercise.setLesson(lesson);
        exercise.setType(type);
        exercise.setContent(content);
        exercise.setCorrectAnswer(correctAnswer);
        if (imageUrl != null) {
            exercise.setImageUrl(imageUrl);
        }
        exerciseRepository.save(exercise);
    }
}