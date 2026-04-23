package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.TopicImportRequest;
import com.languageapp.backend.entity.AdminLog;
import com.languageapp.backend.entity.User;
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
    public ResponseEntity<String> importCurriculum(@RequestBody java.util.List<TopicImportRequest> requests) {

        for (TopicImportRequest request : requests) {
            curriculumService.importTopicAndLessons(request);
        }

        return ResponseEntity.ok("Curriculum imported successfully!");
    }

    @DeleteMapping("/curriculum/topic/{id}")
    public ResponseEntity<Void> deleteTopic(@PathVariable UUID id, Authentication auth) {
        adminService.deleteTopic(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/curriculum/lesson/{id}")
    public ResponseEntity<Void> deleteLesson(@PathVariable UUID id, Authentication auth) {
        adminService.deleteLesson(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/curriculum/exercise/{id}")
    public ResponseEntity<Void> deleteExercise(@PathVariable UUID id, Authentication auth) {
        adminService.deleteExercise(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    // --- USER MANAGEMENT ---
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
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
    public ResponseEntity<String> importAchievements(@RequestBody java.util.List<com.languageapp.backend.entity.Achievement> achievements) {
        adminService.importAchievements(achievements);
        return ResponseEntity.ok("Achievements imported successfully!");
    }

    @DeleteMapping("/achievements/{id}")
    public ResponseEntity<Void> deleteAchievement(@PathVariable UUID id, Authentication auth) {
        adminService.deleteAchievement(id, auth.getName());
        return ResponseEntity.noContent().build();
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