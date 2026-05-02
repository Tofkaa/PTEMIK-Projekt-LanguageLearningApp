package com.languageapp.backend.config;

import com.languageapp.backend.dto.request.TopicImportRequest;
import com.languageapp.backend.entity.Achievement;
import com.languageapp.backend.repository.AchievementRepository;
import com.languageapp.backend.repository.LessonTopicRepository;
import com.languageapp.backend.service.CurriculumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;

/**
 * Component responsible for bootstrapping the database with initial learning materials.
 * * ARCHITECTURE NOTE: Instead of hardcoding entities, this seeder reads a structured
 * JSON file from the resources folder and delegates the actual persistence to the
 * existing {@link CurriculumService}. This ensures DRY (Don't Repeat Yourself) principles
 * and tests the exact same import logic used by the Admin REST endpoints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final LessonTopicRepository topicRepository;
    private final AchievementRepository achievementRepository;
    private final CurriculumService curriculumService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String @NonNull ... args) {
        if (topicRepository.count() == 0) {
            log.info("Database is empty. Initializing structured seed data from JSON...");
            seedCurriculumFromJson();
        }

        if (achievementRepository.count() == 0) {
            log.info("No achievements found. Initializing default achievements...");
            seedAchievementsFromJson();
        }
    }

    private void seedCurriculumFromJson() {
        try {
            InputStream inputStream = new ClassPathResource("data/curriculum-seed.json").getInputStream();
            List<TopicImportRequest> topics = objectMapper.readValue(inputStream, new TypeReference<List<TopicImportRequest>>() {});
            for (TopicImportRequest topicReq : topics) {
                curriculumService.importTopicAndLessons(topicReq);
            }
            log.info("Curriculum seed data initialization completed successfully.");
        } catch (Exception e) {
            log.error("Failed to seed curriculum from JSON file: {}", e.getMessage(), e);
        }
    }

    private void seedAchievementsFromJson() {
        try {
            InputStream inputStream = new ClassPathResource("data/achievements-seed.json").getInputStream();
            List<Achievement> achievements = objectMapper.readValue(inputStream, new TypeReference<List<Achievement>>() {});
            achievementRepository.saveAll(achievements);
            log.info("Successfully seeded {} achievements.", achievements.size());
        } catch (Exception e) {
            log.error("Failed to seed achievements from JSON: {}", e.getMessage(), e);
        }
    }
}
