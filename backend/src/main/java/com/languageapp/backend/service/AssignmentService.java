package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.dto.response.AssignmentResponse;
import com.languageapp.backend.dto.response.AssignmentSessionResponse;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.languageapp.backend.dto.request.ExerciseSubmission;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    public List<AssignmentResponse> getAssignmentsByClassroom(UUID classroomId, String email) {
        return assignmentRepository.findAllByClassroom_ClassroomIdOrderByCreatedAtDesc(classroomId)
                .stream()
                .map(a -> mapToResponse(a, email))
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
                .map(a -> mapToResponse(a, email))
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

        // --- 1. ÜTEMEZÉS VÉDELME ---
        if (assignment.getAvailableFrom() != null && LocalDateTime.now().isBefore(assignment.getAvailableFrom())) {
            throw new BadRequestException("Ez a feladat még nem elérhető!");
        }

        // --- 2. TÖBBSZÖRÖS PRÓBÁLKOZÁSOK KEZELÉSE ---
        List<AssignmentSession> userSessions = sessionRepository.findAllByAssignment_AssignmentIdAndUser_UserId(assignmentId, user.getUserId());

        // Keresünk egy folyamatban lévő (nem befejezett) munkamenetet
        Optional<AssignmentSession> ongoingSession = userSessions.stream()
                .filter(s -> s.getFinishedAt() == null)
                .findFirst();

        AssignmentSession session;
        if (ongoingSession.isPresent()) {
            // Ha van folyamatban lévő, azt folytatja
            session = ongoingSession.get();
            log.info("Student {} is resuming assignment {}", email, assignmentId);
        } else {
            // Ha nincs folyamatban lévő, megnézzük, hányszor adta már be
            long completedCount = userSessions.stream().filter(s -> s.getFinishedAt() != null).count();

            // Ha van limit, és elérte, nem engedjük elindítani
            if (assignment.getMaxAttempts() != null && completedCount >= assignment.getMaxAttempts()) {
                throw new BadRequestException("Maximum próbálkozások száma elérve!");
            }

            // Új munkamenet indítása
            session = new AssignmentSession();
            session.setAssignment(assignment);
            session.setUser(user);
            session.setStartedAt(LocalDateTime.now());
            session = sessionRepository.save(session);
            log.info("Student {} started attempt {} for assignment {}", email, completedCount + 1, assignmentId);
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

        List<AssignmentSessionResponse.AnswerDetail> detailedAnswers = new ArrayList<>();

        for (ExerciseSubmission sub : request.getAnswers()) {
            // Kikeresjük az eredeti feladatot
            Exercise ex = assignmentExercises.stream()
                    .filter(e -> e.getExerciseId().equals(sub.getExerciseId()))
                    .findFirst().orElse(null);

            // Kinyerjük a kérdés szövegét
            String questionText = "Ismeretlen kérdés";
            if (ex != null && ex.getContent() != null) {
                if (ex.getContent().containsKey("question")) {
                    questionText = ex.getContent().get("question").toString();
                } else {
                    questionText = ex.getType().toString();
                }
            }

            // Megnézzük, hogy az értékelő motor szerint ez a válasz hibás volt-e (benne van-e a mistakes listában)
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
            // Az új gazdag listát mentjük el a nyers ExerciseSubmission helyett!
            session.setRawAnswers(objectMapper.writeValueAsString(detailedAnswers));
        } catch (Exception e) {
            log.error("Hiba a válaszok JSON-re alakításakor", e);
        }

        sessionRepository.save(session);

        log.info("Student {} successfully submitted assignment '{}'. Score: {}%",
                email, session.getAssignment().getTitle(), finalScore);
    }

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
     * Lekéri egy adott feladat beadott munkáit a tanár számára.
     */
    @Transactional(readOnly = true)
    public List<AssignmentSessionResponse> getSessionsForAssignment(UUID assignmentId, String teacherEmail) {
        ClassroomAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found."));

        // Csak a feladatot kiíró tanár láthatja a beadott munkákat
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
                            // JAVÍTVA: A letisztult TypeReference beolvasás
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
     * Tanári értékelés elmentése és publikálása
     */
    @Transactional
    public void gradeSession(UUID sessionId, TeacherGradeRequest request, String teacherEmail) {
        AssignmentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found."));

        // Csak a tanár értékelhet
        if (!session.getAssignment().getClassroom().getTeacher().getEmail().equals(teacherEmail)) {
            throw new BadRequestException("Unauthorized to grade this session.");
        }

        session.setTeacherScore(request.getTeacherScore());
        session.setTeacherComment(request.getTeacherComment());
        session.setGraded(true); // Ez a flag engedélyezi majd a diáknál az "Eredmény megtekintése" gombot!

        sessionRepository.save(session);
        log.info("Teacher {} graded session {}. Published: true", teacherEmail, sessionId);
    }

    private AssignmentResponse mapToResponse(ClassroomAssignment a, String email) {
        boolean isCompleted = false;
        int attemptsUsed = 0; // Új változó a próbálkozások nyomon követésére

        if (email != null) {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                // Lekérjük az összes eddigi munkamenetet (listát kapunk vissza)
                List<AssignmentSession> sessions = sessionRepository.findAllByAssignment_AssignmentIdAndUser_UserId(a.getAssignmentId(), userOpt.get().getUserId());

                // Megszámoljuk, hányat fejezett be sikeresen
                attemptsUsed = (int) sessions.stream().filter(session -> session.getFinishedAt() != null).count();

                // Ha legalább egyszer beadta, a státusz befejezett lesz
                isCompleted = attemptsUsed > 0;
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
                attemptsUsed // Az új adat beillesztése a konstruktor végére
        );
    }
}