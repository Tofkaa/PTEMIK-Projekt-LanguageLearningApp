package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.AssignmentCreateRequest;
import com.languageapp.backend.dto.response.AssignmentResponse;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


/**
 * Service handling business logic for classroom assignments, testing, and sessions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssigmentService {
    private final ClassroomAssignmentRepository assignmentRepository;
    private final ClassroomRepository classroomRepository;
    private final ExerciseRepository exerciseRepository; // Feltételezve, hogy létezik
    private final UserRepository userRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final AssignmentSessionRepository sessionRepository;


    /**
     * Creates a new assignment for a classroom.
     * Validates teacher ownership and existence of selected exercises.
     *
     * @param classroomId Target classroom
     * @param request Data containing assignment rules and exercises
     * @param teacherEmail Email of the teacher performing the action
     */
    @Transactional
    public void createAssignment(UUID classroomId, AssignmentCreateRequest request, String teacherEmail) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found."));

        if (!classroom.getTeacher().getEmail().equals(teacherEmail)) {
            throw new BadRequestException("Nincs jogosultságod feladatot kiosztani ebben az osztályban.");
        }

        ClassroomAssignment assignment = new ClassroomAssignment();
        assignment.setClassroom(classroom);
        assignment.setTitle(request.getTitle());
        assignment.setDescription(request.getDescription());
        assignment.setTest(request.isTest());
        assignment.setRandomized(request.isRandomized());
        assignment.setAllowRetries(request.isAllowRetries());
        assignment.setAvailableFrom(request.getAvailableFrom());
        assignment.setAvailableUntil(request.getAvailableUntil());
        assignment.setTimeLimitMinutes(request.getTimeLimitMinutes());

        List<Exercise> exercises = exerciseRepository.findAllById(request.getExerciseIds());
        if (exercises.isEmpty()) {
            throw new BadRequestException("Legalább egy érvényes feladatot ki kell választani.");
        }
        assignment.setExercises(exercises);

        assignmentRepository.save(assignment);
    }

    /**
     * Retrieves all assignments for a specific classroom.
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignmentsByClassroom(UUID classroomId) {
        return assignmentRepository.findAllByClassroom_ClassroomIdOrderByCreatedAtDesc(classroomId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves all currently active/available assignments for a student.
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getActiveAssignmentsForStudent(String email) {
        User student = userRepository.findByEmail(email).orElseThrow();
        return assignmentRepository.findActiveAssignmentsForStudent(student.getUserId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Initiates or resumes an assignment session for a student.
     * Handles timer initialization and randomized exercise selection.
     *
     * @param assignmentId The assignment to start
     * @param email The student's email
     * @return Shuffled list of exercises for the student to solve
     */
    @Transactional
    public List<Exercise> startAssignment(UUID assignmentId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        ClassroomAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found."));

        // Verify student membership in the classroom
        boolean isMember = classroomMemberRepository.existsByClassroom_ClassroomIdAndUser_UserId(
                assignment.getClassroom().getClassroomId(), user.getUserId());

        if (!isMember) {
            throw new BadRequestException("Nem vagy tagja ennek az osztálynak.");
        }

        // Check for an existing session to prevent timer reset or unauthorized retries
        Optional<AssignmentSession> existingSession = sessionRepository
                .findByAssignment_AssignmentIdAndUser_UserId(assignmentId, user.getUserId());

        if (existingSession.isPresent()) {
            AssignmentSession session = existingSession.get();

            if (session.getFinishedAt() != null && !assignment.isAllowRetries()) {
                throw new BadRequestException("Ezt a tesztet már kitöltötted, és nincs lehetőség javításra.");
            }

            log.info("Student {} is resuming assignment {}", email, assignmentId);
        } else {
            // Initialize new session (start timer)
            AssignmentSession newSession = new AssignmentSession();
            newSession.setAssignment(assignment);
            newSession.setUser(user);
            newSession.setStartedAt(LocalDateTime.now());

            sessionRepository.save(newSession);
            log.info("Student {} started assignment {} at {}", email, assignmentId, newSession.getStartedAt());
        }

        List<Exercise> exercises = new ArrayList<>(assignment.getExercises());
        if (assignment.isRandomized()) {
            java.util.Collections.shuffle(exercises);
        }

        return exercises;
    }

    /**
     * Internal helper to map Entity to Response DTO.
     */
    private AssignmentResponse mapToResponse(ClassroomAssignment a) {
        return new AssignmentResponse(
                a.getAssignmentId(),
                a.getTitle(),
                a.getDescription(),
                a.isTest(),
                a.getTimeLimitMinutes(),
                a.getAvailableFrom(),
                a.getAvailableUntil(),
                a.getExercises().size(),
                a.isRandomized(),
                a.isAllowRetries()
        );
    }
}
