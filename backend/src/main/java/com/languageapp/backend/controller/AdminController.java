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
    public ResponseEntity<String> importCurriculum(@RequestBody TopicImportRequest request) {
        curriculumService.importTopicAndLessons(request);
        return ResponseEntity.ok("Curriculum imported successfully!");
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

    // --- SYSTEM LOGS ---
    @GetMapping("/logs")
    public ResponseEntity<List<AdminLog>> getSystemLogs() {
        return ResponseEntity.ok(adminService.getSystemLogs());
    }
}