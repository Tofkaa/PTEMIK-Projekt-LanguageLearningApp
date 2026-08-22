package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.dto.response.*;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.exception.ResourceNotFoundException;
import com.languageapp.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.languageapp.backend.dto.request.ExerciseSubmission;
import com.languageapp.backend.enums.MembershipStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service orchestrating the lifecycle of classroom assignments.
 * Handles the creation, session tracking, anti-cheat generation (dynamic sub-setting),
 * and secure server-side evaluation of student submissions.
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
    private final StreakService streakService;

    private final SseService sseService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a new classroom assignment.
     * Supports standard fixed exercise lists and dynamic "RANDOM_SUBSET" generation for anti-cheat purposes.
     *
     * @param classroomId  The UUID of the classroom where the assignment is created.
     * @param request      The DTO containing scheduling rules, test constraints, and exercise configurations.
     * @param teacherEmail The email of the user attempting to create the assignment (must be the classroom owner).
     * @throws ResourceNotFoundException if the target classroom is not found.
     * @throws BadRequestException if the user is unauthorized or no exercises are selected.
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

        // Anti-Cheat generation settings
        assignment.setGenerationMode(request.getGenerationMode() != null ? request.getGenerationMode() : "FIXED");
        assignment.setQuestionCount(request.getQuestionCount());

        List<Exercise> exercises = exerciseRepository.findAllById(request.getExerciseIds());
        if (exercises.isEmpty()) {
            throw new BadRequestException("At least one valid exercise must be selected.");
        }
        assignment.setExercises(exercises);

        assignmentRepository.save(assignment);

        classroomMemberRepository.findAllByClassroom_ClassroomIdAndStatus(classroomId, MembershipStatus.ACCEPTED)
                .forEach(member -> sseService.sendPing(member.getUser().getEmail()));
    }

    /**
     * Retrieves the list of assignments for a specific classroom (Teacher perspective).
     *
     * @param classroomId The UUID of the target classroom.
     * @param email       The email of the requesting user.
     * @return List of {@link AssignmentResponse} containing metadata and completion status.
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignmentsByClassroom(UUID classroomId, String email) {
        return assignmentRepository.findAllByClassroom_ClassroomIdOrderByCreatedAtDesc(classroomId)
                .stream()
                .map(a -> mapToResponse(a, email))
                .toList();
    }

    /**
     * Retrieves all active, open assignments across all classrooms a student is enrolled in.
     *
     * @param email The email of the student.
     * @return List of currently available {@link AssignmentResponse}.
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getActiveAssignmentsForStudent(String email) {
        User student = userRepository.findByEmail(email).orElseThrow();
        return assignmentRepository.findActiveAssignmentsForStudent(student.getUserId())
                .stream()
                .map(a -> mapToResponse(a, email))
                .toList();
    }

    /**
     * Initiates or resumes an assignment session for a student.
     * Handles the dynamic allocation of exercises based on the assignment's generation mode (e.g., RANDOM_SUBSET).
     *
     * @param assignmentId The UUID of the assignment to start.
     * @param email        The email of the requesting student.
     * @return {@link AssignmentStartResponse} containing session ID, timer details, and the finalized exercise subset.
     * @throws BadRequestException if access is denied, time window is closed, or max attempts are reached.
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

        if (assignment.getAvailableFrom() != null && LocalDateTime.now().isBefore(assignment.getAvailableFrom())) {
            throw new BadRequestException("Ez a feladat még nem elérhető!");
        }

        List<AssignmentSession> userSessions = sessionRepository.findAllByAssignment_AssignmentIdAndUser_UserId(assignmentId, user.getUserId());

        Optional<AssignmentSession> ongoingSession = userSessions.stream()
                .filter(s -> s.getFinishedAt() == null)
                .findFirst();

        AssignmentSession session;
        if (ongoingSession.isPresent()) {
            session = ongoingSession.get();
            log.info("Student {} is resuming assignment {}", email, assignmentId);
        } else {
            long completedCount = userSessions.stream().filter(s -> s.getFinishedAt() != null).count();

            if (assignment.getMaxAttempts() != null && completedCount >= assignment.getMaxAttempts()) {
                throw new BadRequestException("Maximum próbálkozások száma elérve!");
            }

            session = new AssignmentSession();
            session.setAssignment(assignment);
            session.setUser(user);
            session.setStartedAt(LocalDateTime.now());
            session = sessionRepository.save(session);
            log.info("Student {} started attempt {} for assignment {}", email, completedCount + 1, assignmentId);
        }

        List<Exercise> exercises = new ArrayList<>(assignment.getExercises());

        // Anti-Cheat Engine: Shuffle and Subset allocation
        if (assignment.isRandomized() || "RANDOM_SUBSET".equals(assignment.getGenerationMode())) {
            java.util.Collections.shuffle(exercises);
        }

        if ("RANDOM_SUBSET".equals(assignment.getGenerationMode()) && assignment.getQuestionCount() != null && assignment.getQuestionCount() > 0) {
            int limit = Math.min(assignment.getQuestionCount(), exercises.size());
            exercises = exercises.subList(0, limit);
        }

        return new AssignmentStartResponse(
                session.getSessionId(),
                session.getStartedAt(),
                assignment.getTimeLimitMinutes(),
                assignment.isAllowRetries(),
                assignment.isHasFeedback(),
                exercises,
                assignment.getClassroom().getClassroomId()
        );
    }

    /**
     * Evaluates a completed assignment session securely on the server-side.
     * Adjusts the scoring logic dynamically if a subset generation mode was used.
     *
     * @param sessionId The UUID of the active session.
     * @param request   The DTO containing the student's raw answers.
     * @param email     The email of the student submitting the assignment.
     * @throws BadRequestException if the session is invalid, unauthorized, or submitted past the hard deadline.
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

        // Server-side enforcement of the time limit (+2 minutes grace period for network latency)
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

        LessonSubmitRequest dummyRequest = new LessonSubmitRequest();
        dummyRequest.setAnswers(request.getAnswers());
        dummyRequest.setTimeTakenSeconds(0);

        EvaluationService.EvaluationDetails details = evaluationService.calculateEvaluationDetails(
                assignmentExercises,
                dummyRequest,
                mistakes
        );

        // Calculate dynamic total exercises based on generation mode
        int totalExercises = assignmentExercises.size();

        if ("RANDOM_SUBSET".equals(session.getAssignment().getGenerationMode())
                && session.getAssignment().getQuestionCount() != null
                && session.getAssignment().getQuestionCount() > 0) {
            totalExercises = Math.min(session.getAssignment().getQuestionCount(), assignmentExercises.size());
        }

        int correctCount = details.getCorrectCount();
        int finalScore = totalExercises == 0 ? 0 : (int) Math.round(((double) correctCount / totalExercises) * 100);

        session.setFinishedAt(LocalDateTime.now());
        session.setFinalScore(finalScore);

        List<AssignmentSessionResponse.AnswerDetail> detailedAnswers = new ArrayList<>();

        for (ExerciseSubmission sub : request.getAnswers()) {
            Exercise ex = assignmentExercises.stream()
                    .filter(e -> e.getExerciseId().equals(sub.getExerciseId()))
                    .findFirst().orElse(null);

            String questionText = "Ismeretlen kérdés";
            if (ex != null && ex.getContent() != null) {
                if (ex.getContent().containsKey("question")) {
                    questionText = ex.getContent().get("question").toString();
                } else {
                    questionText = ex.getType().toString();
                }
            }

            boolean isCorrect = true;
            for (MistakeDTO m : mistakes) {
                if (m.getQuestion() != null && m.getQuestion().equals(questionText)) {
                    isCorrect = false;
                    break;
                }
            }

            detailedAnswers.add(new AssignmentSessionResponse.AnswerDetail(
                    questionText,
                    sub.getAnswer() != null ? sub.getAnswer().toString() : "Nincs válasz",
                    isCorrect,
                    sub.isRetried(),
                    ex
            ));
        }

        try {
            session.setRawAnswers(objectMapper.writeValueAsString(detailedAnswers));
        } catch (Exception e) {
            log.error("Hiba a válaszok JSON-re alakításakor", e);
        }

        sessionRepository.save(session);
        sseService.sendPing(session.getAssignment().getClassroom().getTeacher().getEmail());

        streakService.updateActivity(session.getUser());

        log.info("Student {} successfully submitted assignment '{}'. Score: {}%",
                email, session.getAssignment().getTitle(), finalScore);
    }

    /**
     * Deletes a classroom assignment and all its associated data.
     *
     * @param assignmentId The UUID of the assignment.
     * @param teacherEmail The email of the requesting user (must be the owner).
     */
    @Transactional
    public void deleteAssignment(UUID assignmentId, String teacherEmail) {
        ClassroomAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found."));

        if (!assignment.getClassroom().getTeacher().getEmail().equals(teacherEmail)) {
            throw new BadRequestException("Unauthorized to delete this assignment.");
        }

        assignmentRepository.delete(assignment);
    }

    /**
     * Retrieves all completed sessions for a specific assignment for grading purposes.
     *
     * @param assignmentId The UUID of the assignment.
     * @param teacherEmail The email of the teacher requesting the data.
     * @return List of {@link AssignmentSessionResponse} with detailed student answers.
     */
    @Transactional(readOnly = true)
    public List<AssignmentSessionResponse> getSessionsForAssignment(UUID assignmentId, String teacherEmail) {
        ClassroomAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found."));

        if (!assignment.getClassroom().getTeacher().getEmail().equals(teacherEmail)) {
            throw new BadRequestException("Unauthorized access to these sessions.");
        }

        return sessionRepository.findAllByAssignment_AssignmentIdOrderByStartedAtDesc(assignmentId)
                .stream()
                .filter(session -> session.getFinishedAt() != null)
                .map(s -> {
                    List<AssignmentSessionResponse.AnswerDetail> answersList = new ArrayList<>();
                    try {
                        if (s.getRawAnswers() != null) {
                            answersList = objectMapper.readValue(s.getRawAnswers(), new TypeReference<List<AssignmentSessionResponse.AnswerDetail>>() {});
                        }
                    } catch (Exception e) {
                        log.error("Hiba a válaszok visszaolvasásakor", e);
                    }

                    return new AssignmentSessionResponse(
                            s.getSessionId(),
                            s.getUser().getName(),
                            s.getUser().getEmail(),
                            s.getStartedAt(),
                            s.getFinishedAt(),
                            s.getFinalScore(),
                            s.getTeacherScore(),
                            s.getTeacherComment(),
                            s.isGraded(),
                            answersList
                    );
                }).toList();
    }

    /**
     * Retrieves a student's own completed sessions for a specific assignment.
     *
     * @param assignmentId The UUID of the assignment.
     * @param email        The email of the requesting student.
     * @return List of {@link AssignmentSessionResponse}.
     */
    @Transactional(readOnly = true)
    public List<AssignmentSessionResponse> getMySessionsForAssignment(UUID assignmentId, String email) {
        User student = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        return sessionRepository.findAllByAssignment_AssignmentIdAndUser_UserId(assignmentId, student.getUserId())
                .stream()
                .filter(session -> session.getFinishedAt() != null)
                .map(s -> {
                    List<AssignmentSessionResponse.AnswerDetail> answersList = new ArrayList<>();
                    try {
                        if (s.getRawAnswers() != null) {
                            answersList = objectMapper.readValue(s.getRawAnswers(), new TypeReference<List<AssignmentSessionResponse.AnswerDetail>>() {});
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse answers", e);
                    }

                    return new AssignmentSessionResponse(
                            s.getSessionId(),
                            s.getUser().getName(),
                            s.getUser().getEmail(),
                            s.getStartedAt(),
                            s.getFinishedAt(),
                            s.getFinalScore(),
                            s.getTeacherScore(),
                            s.getTeacherComment(),
                            s.isGraded(),
                            answersList
                    );
                }).toList();
    }

    /**
     * Saves the teacher's manual evaluation for a specific session and marks it as graded.
     *
     * @param sessionId    The UUID of the session being graded.
     * @param request      The {@link TeacherGradeRequest} containing score and optional comments.
     * @param teacherEmail The email of the teacher performing the grading.
     */
    @Transactional
    public void gradeSession(UUID sessionId, TeacherGradeRequest request, String teacherEmail) {
        AssignmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found."));

        if (!session.getAssignment().getClassroom().getTeacher().getEmail().equals(teacherEmail)) {
            throw new BadRequestException("Unauthorized to grade this session.");
        }

        session.setTeacherScore(request.getTeacherScore());
        session.setTeacherComment(request.getTeacherComment());
        session.setGraded(true);

        sessionRepository.save(session);
        sseService.sendPing(session.getUser().getEmail());

        log.info("Teacher {} graded session {}. Published: true", teacherEmail, sessionId);
    }

    /**
     * Helper method to map a ClassroomAssignment entity to its API response DTO.
     */
    private AssignmentResponse mapToResponse(ClassroomAssignment a, String email) {
        boolean isCompleted = false;
        int attemptsUsed = 0;
        boolean hasGradedSession = false;

        if (email != null) {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                List<AssignmentSession> sessions = sessionRepository.findAllByAssignment_AssignmentIdAndUser_UserId(a.getAssignmentId(), userOpt.get().getUserId());
                attemptsUsed = (int) sessions.stream().filter(session -> session.getFinishedAt() != null).count();
                isCompleted = attemptsUsed > 0;
                hasGradedSession = sessions.stream().anyMatch(AssignmentSession::isGraded);
            }
        }

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
                isCompleted,
                a.getMaxAttempts(),
                attemptsUsed,
                hasGradedSession
        );
    }

    /**
     * Generates aggregated statistics for a classroom dashboard.
     *
     * @param classroomId The UUID of the target classroom.
     * @return {@link ClassroomStatisticsResponse} containing class averages and individual student progress.
     */
    @Transactional(readOnly = true)
    public ClassroomStatisticsResponse getClassroomStatistics(UUID classroomId) {
        List<ClassroomAssignment> assignments = assignmentRepository.findAllByClassroom_ClassroomIdOrderByCreatedAtDesc(classroomId);
        List<User> students = classroomMemberRepository.findAllByClassroom_ClassroomIdAndStatus(classroomId, MembershipStatus.ACCEPTED)
                .stream().map(ClassroomMember::getUser).toList();

        List<ClassroomStatisticsResponse.StudentProgressDTO> progressList = new ArrayList<>();

        for (User student : students) {
            List<AssignmentSession> studentSessions = new ArrayList<>();
            for (ClassroomAssignment assignment : assignments) {
                List<AssignmentSession> sessionsForAssignment = sessionRepository
                        .findAllByAssignment_AssignmentIdAndUser_UserId(assignment.getAssignmentId(), student.getUserId());
                studentSessions.addAll(sessionsForAssignment.stream().filter(s -> s.getFinishedAt() != null).toList());
            }

            long uniqueCompletedCount = studentSessions.stream()
                    .map(s -> s.getAssignment().getAssignmentId())
                    .distinct()
                    .count();

            double avg = studentSessions.stream()
                    .mapToInt(s -> s.getTeacherScore() != null ? s.getTeacherScore() : s.getFinalScore())
                    .average().orElse(0.0);

            String lastAct = studentSessions.stream()
                    .map(s -> s.getFinishedAt().toString())
                    .max(String::compareTo).orElse("Nincs adat");

            progressList.add(new ClassroomStatisticsResponse.StudentProgressDTO(
                    student.getName(), student.getEmail(), (int)uniqueCompletedCount, Math.round(avg * 10.0) / 10.0, lastAct
            ));
        }

        double totalAvg = progressList.stream()
                .mapToDouble(ClassroomStatisticsResponse.StudentProgressDTO::getAverageScore)
                .filter(a -> a > 0).average().orElse(0.0);


        return new ClassroomStatisticsResponse(
                Math.round(totalAvg * 10.0) / 10.0,
                assignments.size(),
                progressList
        );
    }

    /**
     * Retrieves specific classroom statistics tailored for a single student's perspective.
     *
     * @param classroomId The UUID of the target classroom.
     * @param email       The email of the requesting student.
     * @return Map containing progress counters and average comparisons.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getStudentClassroomStats(UUID classroomId, String email) {
        ClassroomStatisticsResponse allStats = getClassroomStatistics(classroomId);

        ClassroomStatisticsResponse.StudentProgressDTO myProgress = allStats.getStudentProgress().stream()
                .filter(p -> p.getStudentEmail().equals(email))
                .findFirst()
                .orElse(null);

        int completedCount = myProgress != null ? myProgress.getCompletedCount() : 0;
        double myAvg = myProgress != null ? myProgress.getAverageScore() : 0.0;

        return java.util.Map.of(
                "completedCount", completedCount,
                "totalAssignments", allStats.getTotalAssignments(),
                "myAverage", myAvg,
                "classAverage", allStats.getClassAverage()
        );
    }
}