package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.AssignmentCreateRequest;
import com.languageapp.backend.dto.request.AssignmentSubmitRequest;
import com.languageapp.backend.dto.request.LessonSubmitRequest;
import com.languageapp.backend.dto.response.AssignmentResponse;
import com.languageapp.backend.dto.response.AssignmentStartResponse;
import com.languageapp.backend.dto.response.MistakeDTO;
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
 * Service orchestrating the lifecycle of classroom assignments.
 * Handles creation, session tracking, and secure server-side evaluation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {
    private final ClassroomAssignmentRepository assignmentRepository;
    private final ClassroomRepository classroomRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final AssignmentSessionRepository sessionRepository;
    private final EvaluationService evaluationService;

    /**
     * Creates a new assignment. Validates teacher authority and existence of exercises.
     */
    @Transactional
    public void createAssignment(UUID classroomId, AssignmentCreateRequest request, String teacherEmail) {
        Classroom classroom = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found."));

        if (!classroom.getTeacher().getEmail().equals(teacherEmail)) {
            throw new BadRequestException("Unauthorized to create assignments in this classroom.");
        }

        ClassroomAssignment assignment = new ClassroomAssignment();
        assignment.setClassroom(classroom);
        assignment.setTitle(request.getTitle());
        assignment.setDescription(request.getDescription());
        assignment.setTest(request.isTest());
        assignment.setRandomized(request.isRandomized());
        assignment.setAllowRetries(request.isAllowRetries());
        assignment.setHasFeedback(request.isHasFeedback());
        assignment.setMaxAttempts(request.getMaxAttempts());
        assignment.setAvailableFrom(request.getAvailableFrom());
        assignment.setAvailableUntil(request.getAvailableUntil());
        assignment.setTimeLimitMinutes(request.getTimeLimitMinutes());

        List<Exercise> exercises = exerciseRepository.findAllById(request.getExerciseIds());
        if (exercises.isEmpty()) {
            throw new BadRequestException("At least one valid exercise must be selected.");
        }
        assignment.setExercises(exercises);

        assignmentRepository.save(assignment);
    }

    /**
     * Retrieves assignment list for teacher dashboard.
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignmentsByClassroom(UUID classroomId) {
        return assignmentRepository.findAllByClassroom_ClassroomIdOrderByCreatedAtDesc(classroomId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves currently open assignments for a student's active classrooms.
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
     * Starts or resumes an assignment session. Initializes start time for the timer.
     * @return Shuffled exercise list and session metadata.
     */
    @Transactional
    public AssignmentStartResponse startAssignment(UUID assignmentId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        ClassroomAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found."));

        boolean isMember = classroomMemberRepository.existsByClassroom_ClassroomIdAndUser_UserId(
                assignment.getClassroom().getClassroomId(), user.getUserId());

        if (!isMember) {
            throw new BadRequestException("Access denied: Not a member of this classroom.");
        }

        AssignmentSession session;
        Optional<AssignmentSession> existingSession = sessionRepository
                .findByAssignment_AssignmentIdAndUser_UserId(assignmentId, user.getUserId());

        if (existingSession.isPresent()) {
            session = existingSession.get();
            if (session.getFinishedAt() != null && !assignment.isAllowRetries()) {
                throw new BadRequestException("Submission locked: Retries are not allowed.");
            }
            log.info("Student {} is resuming assignment {}", email, assignmentId);
        } else {
            session = new AssignmentSession();
            session.setAssignment(assignment);
            session.setUser(user);
            session.setStartedAt(LocalDateTime.now());
            session = sessionRepository.save(session);
            log.info("Student {} started assignment {} at {}", email, assignmentId, session.getStartedAt());
        }

        List<Exercise> exercises = new ArrayList<>(assignment.getExercises());
        if (assignment.isRandomized()) {
            java.util.Collections.shuffle(exercises);
        }

        return new AssignmentStartResponse(
                session.getSessionId(),
                session.getStartedAt(),
                assignment.getTimeLimitMinutes(),
                assignment.isAllowRetries(),
                assignment.isHasFeedback(),
                exercises
        );
    }

    /**
     * Evaluates a student's assignment submission.
     * Enforces time limit validation and uses the core evaluation engine via a dummy request wrapper.
     */
    @Transactional
    public void submitAssignment(UUID sessionId, AssignmentSubmitRequest request, String email) {
        AssignmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found."));

        if (!session.getUser().getEmail().equals(email)) {
            throw new BadRequestException("Unauthorized access to session.");
        }

        if (session.getFinishedAt() != null) {
            throw new BadRequestException("Assignment already submitted.");
        }

        // Enforce server-side time limit validation
        Integer limit = session.getAssignment().getTimeLimitMinutes();
        if (limit != null && limit > 0) {
            LocalDateTime deadline = session.getStartedAt().plusMinutes(limit + 2);
            if (LocalDateTime.now().isAfter(deadline)) {
                log.error("Late submission blocked for user {}.", email);
                throw new BadRequestException("Time limit exceeded! Submission failed.");
            }
        }

        List<Exercise> assignmentExercises = session.getAssignment().getExercises();
        List<MistakeDTO> mistakes = new ArrayList<>();

        // Create a virtual LessonSubmitRequest to reuse the existing core evaluation logic
        LessonSubmitRequest dummyRequest = new LessonSubmitRequest();
        dummyRequest.setAnswers(request.getAnswers());
        dummyRequest.setTimeTakenSeconds(0);

        EvaluationService.EvaluationDetails details = evaluationService.calculateEvaluationDetails(
                assignmentExercises,
                dummyRequest,
                mistakes
        );

        int totalExercises = assignmentExercises.size();
        int correctCount = details.getCorrectCount();
        int finalScore = totalExercises == 0 ? 0 : (int) Math.round(((double) correctCount / totalExercises) * 100);

        session.setFinishedAt(LocalDateTime.now());
        session.setFinalScore(finalScore);
        sessionRepository.save(session);

        log.info("Student {} successfully submitted assignment '{}'. Score: {}%",
                email, session.getAssignment().getTitle(), finalScore);
    }

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
                a.isAllowRetries(),
                a.isHasFeedback(),
                a.getMaxAttempts()
        );
    }
}