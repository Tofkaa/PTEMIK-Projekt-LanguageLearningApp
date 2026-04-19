package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.AssignmentCreateRequest;
import com.languageapp.backend.dto.request.AssignmentSubmitRequest;
import com.languageapp.backend.dto.request.TeacherGradeRequest;
import com.languageapp.backend.dto.response.AssignmentResponse;
import com.languageapp.backend.dto.response.AssignmentSessionResponse;
import com.languageapp.backend.dto.response.AssignmentStartResponse;
import com.languageapp.backend.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    /**
     * Teacher creates a new assignment for a specific classroom.
     */
    @PostMapping("/classroom/{classroomId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> createAssignment(
            @PathVariable UUID classroomId,
            @Valid @RequestBody AssignmentCreateRequest request,
            Authentication auth) {
        assignmentService.createAssignment(classroomId, request, auth.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * Teacher retrieves all assignments created for their classroom.
     */
    @GetMapping("/classroom/{classroomId}")
    public ResponseEntity<List<AssignmentResponse>> getClassroomAssignments(
            @PathVariable UUID classroomId,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.getAssignmentsByClassroom(classroomId, auth.getName()));
    }

    /**
     * Student retrieves their active assignments across all classrooms.
     */
    @GetMapping("/active")
    public ResponseEntity<List<AssignmentResponse>> getMyActiveAssignments(Authentication auth) {
        return ResponseEntity.ok(assignmentService.getActiveAssignmentsForStudent(auth.getName()));
    }

    /**
     * Student starts or resumes an assignment.
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<AssignmentStartResponse> startAssignment(
            @PathVariable UUID id,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.startAssignment(id, auth.getName()));
    }

    /**
     * Student submits the final results for an assignment.
     */
    @PostMapping("/sessions/{sessionId}/submit")
    public ResponseEntity<Void> submitAssignment(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AssignmentSubmitRequest request,
            Authentication auth) {
        assignmentService.submitAssignment(sessionId, request, auth.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable UUID id,
            Authentication auth) {
        assignmentService.deleteAssignment(id, auth.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * Tanár lekéri egy adott feladat összes beadott munkáját.
     */
    @GetMapping("/{id}/sessions")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<AssignmentSessionResponse>> getAssignmentSessions(
            @PathVariable UUID id,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.getSessionsForAssignment(id, auth.getName()));
    }

    /**
     * Tanár beküldi a felülbírált pontszámot és az értékelést (Publikálás).
     */
    @PostMapping("/sessions/{sessionId}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> gradeAssignmentSession(
            @PathVariable UUID sessionId,
            @RequestBody TeacherGradeRequest request,
            Authentication auth) {
        assignmentService.gradeSession(sessionId, request, auth.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/my-sessions")
    public ResponseEntity<List<AssignmentSessionResponse>> getMyAssignmentSessions(
            @PathVariable UUID id,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.getMySessionsForAssignment(id, auth.getName()));
    }
}