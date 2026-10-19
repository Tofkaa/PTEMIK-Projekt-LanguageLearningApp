package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.AssignmentAccommodationRequest;
import com.languageapp.backend.dto.request.AssignmentInitialAccommodationRequest;
import com.languageapp.backend.dto.request.AssignmentCreateRequest;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AssignmentAccommodationTest {
    @Mock ClassroomAssignmentRepository assignmentRepository;
    @Mock ClassroomRepository classroomRepository;
    @Mock ExerciseRepository exerciseRepository;
    @Mock UserRepository userRepository;
    @Mock ClassroomMemberRepository classroomMemberRepository;
    @Mock AssignmentSessionRepository sessionRepository;
    @Mock AssignmentAccommodationRepository accommodationRepository;
    @Mock EvaluationService evaluationService;
    @Mock StreakService streakService;
    @Mock SseService sseService;
    static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    @Spy Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    @InjectMocks AssignmentService service;
    User teacher, student;
    ClassroomAssignment assignment;
    ClassroomMember member;

    @BeforeEach void setup() {
        teacher = new User(); teacher.setUserId(UUID.randomUUID()); teacher.setEmail("teacher@test.invalid");
        student = new User(); student.setUserId(UUID.randomUUID()); student.setEmail("student@test.invalid");
        Classroom classroom = new Classroom(); classroom.setClassroomId(UUID.randomUUID()); classroom.setTeacher(teacher);
        assignment = new ClassroomAssignment(); assignment.setAssignmentId(UUID.randomUUID()); assignment.setClassroom(classroom);
        assignment.setTimeLimitMinutes(10); assignment.setAvailableUntil(NOW.plusSeconds(7200));
        member = new ClassroomMember(); member.setClassroom(classroom); member.setUser(student); member.setStatus(MembershipStatus.ACCEPTED);
        lenient().when(assignmentRepository.findLockedById(assignment.getAssignmentId())).thenReturn(Optional.of(assignment));
        lenient().when(assignmentRepository.findById(assignment.getAssignmentId())).thenReturn(Optional.of(assignment));
        lenient().when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        lenient().when(classroomMemberRepository.findByClassroom_ClassroomIdAndUser_UserId(classroom.getClassroomId(), student.getUserId())).thenReturn(Optional.of(member));
        lenient().when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }
    AssignmentAccommodationRequest request(int extra, boolean unlimited, Instant until) {
        var r = new AssignmentAccommodationRequest(); r.setExtraMinutes(extra); r.setUnlimited(unlimited); r.setAvailableUntilOverride(until); return r;
    }
    AssignmentSession ongoing(Integer minutes) {
        var s = new AssignmentSession(); s.setSessionId(UUID.randomUUID()); s.setAssignment(assignment); s.setUser(student);
        s.setStartedAt(NOW.minusSeconds(120)); s.setTimingInitialized(true); s.setEffectiveTimeLimitMinutes(minutes); s.setEffectiveAvailableUntil(assignment.getAvailableUntil());
        return s;
    }
    void sessions(AssignmentSession... values) {
        when(sessionRepository.findAllByAssignment_AssignmentIdAndUser_UserId(assignment.getAssignmentId(), student.getUserId())).thenReturn(List.of(values));
    }
    @Test void pendingMemberCannotStart() {
        member.setStatus(MembershipStatus.PENDING);
        assertThrows(BadRequestException.class, () -> service.startAssignment(assignment.getAssignmentId(), student.getEmail()));
        verify(sessionRepository, never()).save(any());
    }
    @Test void otherTeacherCannotEdit() {
        assertThrows(BadRequestException.class, () -> service.updateAccommodation(assignment.getAssignmentId(), student.getUserId(), request(10, false, null), "other@test.invalid"));
        verify(accommodationRepository, never()).save(any());
    }
    @Test void nonMemberCannotReceiveAccommodation() {
        when(classroomMemberRepository.findByClassroom_ClassroomIdAndUser_UserId(member.getClassroom().getClassroomId(), student.getUserId())).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.updateAccommodation(assignment.getAssignmentId(), student.getUserId(), request(10, false, null), teacher.getEmail()));
        verify(accommodationRepository, never()).save(any());
    }

    private AssignmentCreateRequest creation(AssignmentInitialAccommodationRequest... initial) {
        when(classroomRepository.findById(member.getClassroom().getClassroomId())).thenReturn(Optional.of(member.getClassroom()));
        var exercise = new Exercise(); exercise.setExerciseId(UUID.randomUUID());
        when(exerciseRepository.findAllById(any())).thenReturn(List.of(exercise));
        var request = new AssignmentCreateRequest(); request.setTitle("Initial timing"); request.setTimeLimitMinutes(10);
        request.setExerciseIds(List.of(exercise.getExerciseId())); request.setAccommodations(Arrays.asList(initial));
        return request;
    }

    private AssignmentInitialAccommodationRequest initial() {
        var request = new AssignmentInitialAccommodationRequest(); request.setUserId(student.getUserId());
        request.setExtraMinutes(15); request.setAvailableUntilOverride(NOW.plusSeconds(3600));
        return request;
    }

    @Test void creationPersistsIndividualTimingWithAssignment() {
        var request = creation(initial());
        service.createAssignment(member.getClassroom().getClassroomId(), request, teacher.getEmail());
        verify(assignmentRepository).save(any());
        verify(accommodationRepository).saveAll(argThat(values -> {
            var ac = values.iterator().next();
            return ac.getUser() == student && ac.getAssignment().getTimeLimitMinutes() == 10
                    && ac.getExtraMinutes() == 15 && ac.getAvailableUntilOverride().equals(NOW.plusSeconds(3600));
        }));
    }

    @Test void creationRejectsForeignStudentBeforePublishing() {
        var foreign = initial(); foreign.setUserId(UUID.randomUUID());
        var request = creation(initial(), foreign);
        assertThrows(BadRequestException.class, () -> service.createAssignment(member.getClassroom().getClassroomId(), request, teacher.getEmail()));
        verify(assignmentRepository, never()).save(any()); verify(accommodationRepository, never()).saveAll(any());
        verifyNoInteractions(sseService);
    }

    @Test void creationRejectsDuplicateStudentBeforePublishing() {
        var request = creation(initial(), initial());
        assertThrows(BadRequestException.class, () -> service.createAssignment(member.getClassroom().getClassroomId(), request, teacher.getEmail()));
        verify(assignmentRepository, never()).save(any());
    }

    @Test void creationRejectsInvalidIndividualDeadlineBeforePublishing() {
        var request = creation(initial()); request.setAvailableFrom(NOW.plusSeconds(7200));
        assertThrows(BadRequestException.class, () -> service.createAssignment(member.getClassroom().getClassroomId(), request, teacher.getEmail()));
        verify(assignmentRepository, never()).save(any());
    }
    @Test void activeTimeCannotBeReduced() {
        var active = ongoing(20); sessions(active);
        assertThrows(BadRequestException.class, () -> service.updateAccommodation(assignment.getAssignmentId(), student.getUserId(), request(0, false, null), teacher.getEmail()));
        assertEquals(20, active.getEffectiveTimeLimitMinutes());
        verify(accommodationRepository, never()).save(any());
    }
    @Test void activeUnlimitedCannotBecomeFinite() {
        sessions(ongoing(null));
        assertThrows(BadRequestException.class, () -> service.updateAccommodation(assignment.getAssignmentId(), student.getUserId(), request(100, false, null), teacher.getEmail()));
    }
    @Test void activeDeadlineCannotBeShortened() {
        var active = ongoing(10); sessions(active);
        assertThrows(BadRequestException.class, () -> service.updateAccommodation(assignment.getAssignmentId(), student.getUserId(), request(10, false, NOW.plusSeconds(3600)), teacher.getEmail()));
    }
    @Test void extensionUpdatesActiveAttemptButNotFinishedAttempt() {
        var active = ongoing(10); var finished = ongoing(10); finished.setFinishedAt(NOW);
        sessions(active, finished);
        service.updateAccommodation(assignment.getAssignmentId(), student.getUserId(), request(20, false, NOW.plusSeconds(10800)), teacher.getEmail());
        assertEquals(30, active.getEffectiveTimeLimitMinutes());
        assertEquals(NOW.plusSeconds(10800), active.getEffectiveAvailableUntil());
        assertEquals(NOW.minusSeconds(120), active.getStartedAt());
        assertEquals(10, finished.getEffectiveTimeLimitMinutes());
        assertEquals(NOW.plusSeconds(7200), finished.getEffectiveAvailableUntil());
        verify(sseService).sendPing(student.getEmail());
    }
    @Test void unlimitedStillRetainsSeparateDeadline() {
        var active = ongoing(10); sessions(active);
        service.updateAccommodation(assignment.getAssignmentId(), student.getUserId(), request(0, true, null), teacher.getEmail());
        assertNull(active.getEffectiveTimeLimitMinutes());
        assertEquals(assignment.getAvailableUntil(), active.getEffectiveAvailableUntil());
    }
    @Test void individualDeadlineAllowsStartingAfterCommonDeadline() {
        assignment.setAvailableUntil(NOW.minusSeconds(1));
        var ac = new AssignmentAccommodation(); ac.setExtraMinutes(5); ac.setAvailableUntilOverride(NOW.plusSeconds(1200));
        when(accommodationRepository.findByAssignment_AssignmentIdAndUser_UserId(assignment.getAssignmentId(), student.getUserId())).thenReturn(Optional.of(ac));
        var result = service.startAssignment(assignment.getAssignmentId(), student.getEmail());
        assertEquals(15, result.getTimeLimitMinutes());
        assertEquals(NOW.plusSeconds(900), result.getTiming().deadline());
        assertEquals(NOW.plusSeconds(1200), result.getTiming().availableUntil());
    }
    @Test void exactIndividualDeadlineRejectsNewAttempt() {
        assignment.setAvailableUntil(NOW);
        assertThrows(BadRequestException.class, () -> service.startAssignment(assignment.getAssignmentId(), student.getEmail()));
        verify(sessionRepository, never()).save(any());
    }
    @Test void timingReadIsOwnedAndUsesSnapshotAndServerClock() {
        var active = ongoing(30);
        active.setEffectiveAvailableUntil(NOW.plusSeconds(60));
        when(sessionRepository.findById(active.getSessionId())).thenReturn(Optional.of(active));
        var result = service.getSessionTiming(active.getSessionId(), student.getEmail());
        assertEquals(NOW.plusSeconds(60), result.deadline());
        assertEquals(NOW, result.serverNow());
        assertThrows(BadRequestException.class, () -> service.getSessionTiming(active.getSessionId(), "stranger@test.invalid"));
    }
    @Test void legacyFinishedAttemptDoesNotInheritNewAccommodation() {
        var finished = ongoing(30); finished.setFinishedAt(NOW); finished.setTimingInitialized(false);
        when(sessionRepository.findById(finished.getSessionId())).thenReturn(Optional.of(finished));
        assertEquals(10, service.getSessionTiming(finished.getSessionId(), teacher.getEmail()).timeLimitMinutes());
        assertFalse(finished.isTimingInitialized());
        verifyNoInteractions(accommodationRepository);
    }
}
