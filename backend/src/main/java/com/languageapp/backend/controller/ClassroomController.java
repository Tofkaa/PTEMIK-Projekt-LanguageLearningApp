package com.languageapp.backend.controller;

import com.languageapp.backend.dto.request.ClassroomCreateRequest;
import com.languageapp.backend.dto.request.JoinClassroomRequest;
import com.languageapp.backend.dto.response.ClassroomMemberResponse;
import com.languageapp.backend.dto.response.ClassroomResponse;
import com.languageapp.backend.dto.projection.ClassroomMemberStatDTO;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.service.ClassroomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing virtual classrooms and student memberships.
 */
@RestController
@RequestMapping("/api/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;


    /**
     * New classroom creation
     */
    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ClassroomResponse> createClassroom(
            @Valid @RequestBody ClassroomCreateRequest request,
            Authentication authentication) {
        ClassroomResponse response = classroomService.createClassroom(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List classrooms for a logged in teacher user
     */
    @GetMapping("/teacher")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<ClassroomResponse>> getTeacherClassrooms(Authentication authentication) {
        return ResponseEntity.ok(classroomService.getClassroomsForTeacher(authentication.getName()));
    }

    /**
     * Get classroom members
     */
    @GetMapping("/{classroomId}/members")
    public ResponseEntity<List<ClassroomMemberResponse>> getClassroomMembers(
            @PathVariable UUID classroomId,
            @RequestParam MembershipStatus status,
            Authentication authentication) {
        return ResponseEntity.ok(classroomService.getClassroomMembers(classroomId, status, authentication.getName()));
    }

    /**
     * Moderate student join requests
     */
    @PatchMapping("/{classroomId}/members/{studentId}/moderate")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> moderateJoinRequest(
            @PathVariable UUID classroomId,
            @PathVariable UUID studentId,
            @RequestParam boolean approve,
            Authentication authentication) {
        classroomService.moderateJoinRequest(classroomId, studentId, authentication.getName(), approve);
        return ResponseEntity.ok().build();
    }

    /**
     * Get student stats for dashboard
     */
    @GetMapping("/{classroomId}/stats")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<ClassroomMemberStatDTO>> getClassroomStats(
            @PathVariable UUID classroomId,
            Authentication authentication) {
        return ResponseEntity.ok(classroomService.getClassroomStats(classroomId, authentication.getName()));
    }

    /**
     * Removes a student from a classroom (Kick functionality).
     */
    @DeleteMapping("/{classroomId}/members/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> kickStudent(
            @PathVariable UUID classroomId,
            @PathVariable UUID studentId,
            Authentication authentication) {
        classroomService.kickStudent(classroomId, studentId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * Join classroom with invite code
     */
    @PostMapping("/join")
    public ResponseEntity<Void> joinClassroom(
            @Valid @RequestBody JoinClassroomRequest request,
            Authentication authentication) {
        classroomService.joinClassroom(request.getInviteCode(), authentication.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * List classrooms for student
     */
    @GetMapping("/student")
    public ResponseEntity<List<ClassroomResponse>> getStudentClassrooms(Authentication authentication) {
        return ResponseEntity.ok(classroomService.getClassroomsForStudent(authentication.getName()));
    }
}