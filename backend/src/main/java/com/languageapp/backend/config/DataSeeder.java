package com.languageapp.backend.config;

import com.languageapp.backend.dto.request.TopicImportRequest;
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
    private final CurriculumService curriculumService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String @NonNull ... args) {
        // Safeguard: Only run initialization if the curriculum table is completely empty
        if (topicRepository.count() == 0) {
            log.info("Database is empty. Initializing structured seed data from JSON...");
            seedFromJson();
            log.info("Seed data initialization completed successfully.");
        } else {
            log.info("Database already contains learning materials. Seeding skipped.");
        }
    }

    /**
     * Parses the JSON seed file and processes it via the application's core business logic.
     */
    private void seedFromJson() {
        try {
            InputStream inputStream = new ClassPathResource("data/curriculum-seed.json").getInputStream();

            // Map the JSON array directly to our standard Import DTOs
            List<TopicImportRequest> topics = objectMapper.readValue(inputStream, new TypeReference<List<TopicImportRequest>>() {});

            // Delegate to the CurriculumService (reusing core business logic)
            for (TopicImportRequest topicReq : topics) {
                curriculumService.importTopicAndLessons(topicReq);
            }
        } catch (Exception e) {
            log.error("Failed to seed database from JSON file: {}", e.getMessage(), e);
        }
    }
}