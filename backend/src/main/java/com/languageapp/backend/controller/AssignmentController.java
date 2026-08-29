package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.AssignmentCreateRequest;
import com.languageapp.backend.dto.request.AssignmentSubmitRequest;
import com.languageapp.backend.dto.request.TeacherGradeRequest;
import com.languageapp.backend.dto.response.*;
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
     * Teacher retrieves all submissions to a specific assignment.
     */
    @GetMapping("/{id}/sessions")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<AssignmentSessionResponse>> getAssignmentSessions(
            @PathVariable UUID id,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.getSessionsForAssignment(id, auth.getName()));
    }

    /**
     * Teacher submits the overridden score and the assessment (Publish).
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

    @GetMapping("/classroom/{classroomId}/statistics")
    public ResponseEntity<ClassroomStatisticsResponse> getClassroomStatistics(@PathVariable UUID classroomId) {
        return ResponseEntity.ok(assignmentService.getClassroomStatistics(classroomId));
    }


    @GetMapping("/classroom/{classroomId}/my-statistics")
    public ResponseEntity<java.util.Map<String, Object>> getMyClassroomStatistics(
            @PathVariable UUID classroomId,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.getStudentClassroomStats(classroomId, auth.getName()));
    }

    /**
     * Teacher retrieves advanced analytics (heatmap and item analysis) for a classroom.
     */
    @GetMapping("/classroom/{classroomId}/advanced-statistics")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassroomAnalyticsResponse> getClassroomAdvancedStatistics(
            @PathVariable UUID classroomId,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.getClassroomAnalytics(classroomId, auth.getName()));
    }
}