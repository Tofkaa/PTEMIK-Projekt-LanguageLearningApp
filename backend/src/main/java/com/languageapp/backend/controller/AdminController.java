package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.TopicImportRequest;
import com.languageapp.backend.enums.Role;
import com.languageapp.backend.service.AdminService;
import com.languageapp.backend.service.CurriculumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final CurriculumService curriculumService;
    private final AdminService adminService;

    // --- CURRICULUM MANAGEMENT ---
    @PostMapping("/curriculum/import")
    public ResponseEntity<String> importCurriculum(@RequestBody java.util.List<TopicImportRequest> requests, Authentication auth) {
        for (TopicImportRequest request : requests) {
            curriculumService.importTopicAndLessons(request);
        }
        adminService.logImportAction(auth.getName(), "CURRICULUM_IMPORTED", requests.size() + " teljes témakör importálva.");
        return ResponseEntity.ok("Curriculum imported successfully!");
    }

    @PutMapping("/curriculum/topic/{id}/status")
    public ResponseEntity<String> toggleTopicStatus(@PathVariable UUID id, @RequestParam boolean isActive, Authentication auth) {
        adminService.toggleTopicStatus(id, isActive, auth.getName());
        return ResponseEntity.ok("Topic status updated.");
    }

    @PutMapping("/curriculum/lesson/{id}/status")
    public ResponseEntity<String> toggleLessonStatus(@PathVariable UUID id, @RequestParam boolean isActive, Authentication auth) {
        adminService.toggleLessonStatus(id, isActive, auth.getName());
        return ResponseEntity.ok("Lesson status updated.");
    }

    @PutMapping("/curriculum/exercise/{id}/status")
    public ResponseEntity<String> toggleExerciseStatus(@PathVariable UUID id, @RequestParam boolean isActive, Authentication auth) {
        adminService.toggleExerciseStatus(id, isActive, auth.getName());
        return ResponseEntity.ok("Exercise status updated.");
    }

    @GetMapping("/curriculum/topics")
    public ResponseEntity<List<com.languageapp.backend.dto.response.TopicAdminResponse>> getAllTopics() {
        return ResponseEntity.ok(adminService.getAllTopics());
    }

    @PostMapping("/curriculum/topic/{topicId}/lesson/import")
    public ResponseEntity<String> importLessonsToTopic(
            @PathVariable UUID topicId,
            @RequestBody List<com.languageapp.backend.dto.request.LessonImportRequest> requests,
            Authentication auth) {

        curriculumService.importLessonsToExistingTopic(topicId, requests);
        adminService.logImportAction(auth.getName(), "LESSON_IMPORTED", requests.size() + " új lecke importálva egy meglévő témakörhöz.");
        return ResponseEntity.ok("Lessons imported successfully to topic!");
    }

    @PostMapping("/curriculum/lesson/{lessonId}/exercise/import")
    public ResponseEntity<String> importExercisesToLesson(
            @PathVariable UUID lessonId,
            @RequestBody List<com.languageapp.backend.dto.request.ExerciseImportRequest> requests,
            Authentication auth) {

        curriculumService.importExercisesToExistingLesson(lessonId, requests);
        adminService.logImportAction(auth.getName(), "EXERCISE_IMPORTED", requests.size() + " új feladat importálva egy meglévő leckéhez.");
        return ResponseEntity.ok("Exercises imported successfully to lesson!");
    }


    @GetMapping("/users")
    public ResponseEntity<List<AdminService.UserAdminDTO>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<String> updateUserRole(
            @PathVariable UUID userId,
            @RequestParam Role newRole,
            Authentication authentication) {

        adminService.updateUserRole(userId, newRole, authentication.getName());
        return ResponseEntity.ok("User role updated successfully.");
    }

    @PutMapping("/users/{userId}/status")
    public ResponseEntity<String> toggleUserStatus(
            @PathVariable UUID userId,
            @RequestParam boolean isActive,
            Authentication authentication) {

        adminService.toggleUserStatus(userId, isActive, authentication.getName());
        return ResponseEntity.ok("User status updated successfully.");
    }

    // --- ACHIEVEMENT MANAGEMENT ---
    @PostMapping("/achievements/import")
    public ResponseEntity<String> importAchievements(@RequestBody java.util.List<com.languageapp.backend.entity.Achievement> achievements, Authentication auth) {
        adminService.importAchievements(achievements);
        adminService.logImportAction(auth.getName(), "ACHIEVEMENT_IMPORTED", achievements.size() + " új kitüntetés importálva.");
        return ResponseEntity.ok("Achievements imported successfully!");
    }

    @PutMapping("/achievements/{id}/status")
    public ResponseEntity<String> toggleAchievementStatus(@PathVariable UUID id, @RequestParam boolean isActive, Authentication auth) {
        adminService.toggleAchievementStatus(id, isActive, auth.getName());
        return ResponseEntity.ok("Achievement status updated.");
    }

    @GetMapping("/achievements")
    public ResponseEntity<List<com.languageapp.backend.entity.Achievement>> getAllAchievements() {
        return ResponseEntity.ok(adminService.getAllAchievements());
    }

    // --- CLASSROOM MANAGEMENT ---
    @GetMapping("/classrooms")
    public ResponseEntity<List<com.languageapp.backend.dto.response.ClassroomAdminResponse>> getAllClassrooms() {
        return ResponseEntity.ok(adminService.getAllClassrooms());
    }

    @PutMapping("/classrooms/{id}/status")
    public ResponseEntity<String> toggleClassroomStatus(
            @PathVariable UUID id,
            @RequestParam boolean isActive,
            Authentication auth) {
        adminService.toggleClassroomStatus(id, isActive, auth.getName());
        return ResponseEntity.ok("Classroom status updated.");
    }

    // --- SYSTEM LOGS ---
    @GetMapping("/logs")
    public ResponseEntity<List<com.languageapp.backend.dto.response.AdminLogResponse>> getSystemLogs() {
        return ResponseEntity.ok(adminService.getSystemLogs());
    }
}