package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.TopicImportRequest;
import com.languageapp.backend.service.CurriculumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CurriculumService curriculumService;

    @PostMapping("/curriculum/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> importCurriculum(@RequestBody TopicImportRequest request) {
        curriculumService.importTopicAndLessons(request);
        return ResponseEntity.ok("Curriculum imported successfully!");
    }
}