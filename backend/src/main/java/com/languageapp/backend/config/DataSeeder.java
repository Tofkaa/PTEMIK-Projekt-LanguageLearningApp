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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Component responsible for bootstrapping the database with initial learning materials.
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

        log.info("Checking and synchronizing achievements...");
        syncAchievementsFromJson();
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

    private void syncAchievementsFromJson() {
        try {
            InputStream inputStream = new ClassPathResource("data/achievements-seed.json").getInputStream();
            List<Achievement> seedAchievements = objectMapper.readValue(inputStream, new TypeReference<List<Achievement>>() {});

            List<Achievement> existingAchievements = achievementRepository.findAll();
            Set<String> existingNames = existingAchievements.stream()
                    .map(Achievement::getName)
                    .collect(Collectors.toSet());


            List<Achievement> newAchievements = seedAchievements.stream()
                    .filter(ach -> !existingNames.contains(ach.getName()))
                    .toList();

            if (!newAchievements.isEmpty()) {
                achievementRepository.saveAll(newAchievements);
                log.info("Successfully seeded {} NEW achievements.", newAchievements.size());
            } else {
                log.info("All achievements are already up to date.");
            }
        } catch (Exception e) {
            log.error("Failed to sync achievements from JSON: {}", e.getMessage(), e);
        }
    }
}