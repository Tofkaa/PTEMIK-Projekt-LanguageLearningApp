package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.*;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentRecoveryTest {
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
    @Mock Clock clock;
    @InjectMocks AssignmentService service;
    final Instant now = Instant.parse("2026-10-24T16:00:00Z");
    User student; ClassroomAssignment assignment; AssignmentSession session; ClassroomMember member;
    Exercise first, second;

    @BeforeEach void setup() {
        lenient().when(clock.instant()).thenReturn(now);
        student = new User(); student.setUserId(UUID.randomUUID()); student.setEmail("student@example.test");
        User teacher = new User(); teacher.setEmail("teacher@example.test");
        Classroom room = new Classroom(); room.setClassroomId(UUID.randomUUID()); room.setTeacher(teacher);
        assignment = new ClassroomAssignment(); assignment.setAssignmentId(UUID.randomUUID()); assignment.setClassroom(room);
        assignment.setTitle("Recovery test"); assignment.setRandomized(false); assignment.setAllowLateSubmission(false);
        assignment.setTimeLimitMinutes(10);
        first = exercise(); second = exercise(); assignment.setExercises(new ArrayList<>(List.of(first, second)));
        session = new AssignmentSession(); session.setSessionId(UUID.randomUUID()); session.setUser(student); session.setAssignment(assignment);
        session.setStartedAt(now.minusSeconds(60));
        member = new ClassroomMember(); member.setUser(student); member.setClassroom(room); member.setStatus(MembershipStatus.ACCEPTED);
        lenient().when(sessionRepository.findAssignmentId(session.getSessionId())).thenReturn(Optional.of(assignment.getAssignmentId()));
        lenient().when(sessionRepository.findById(session.getSessionId())).thenReturn(Optional.of(session));
        lenient().when(sessionRepository.findAllByAssignment_AssignmentIdAndUser_UserId(assignment.getAssignmentId(),student.getUserId())).thenReturn(List.of(session));
        lenient().when(assignmentRepository.findLockedById(assignment.getAssignmentId())).thenReturn(Optional.of(assignment));
        lenient().when(userRepository.findByEmail(student.getEmail())).thenReturn(Optional.of(student));
        lenient().when(classroomMemberRepository.findByClassroom_ClassroomIdAndUser_UserId(room.getClassroomId(),student.getUserId())).thenReturn(Optional.of(member));
        lenient().when(evaluationService.calculateEvaluationDetails(any(),any(),any())).thenReturn(new EvaluationService.EvaluationDetails(1,0));
    }
    Exercise exercise() { Exercise e = new Exercise(); e.setExerciseId(UUID.randomUUID()); e.setType("TRANSLATION"); e.setContent(Map.of("question","Alma")); return e; }
    AssignmentDraftRequest draft() {
        var d = new AssignmentDraftRequest(); d.setRevision(session.getDraftRevision());
        d.setQueue(List.of(first.getExerciseId(), second.getExerciseId())); d.setCurrentAnswer("saved apple"); return d;
    }
    ExerciseSubmission answer(UUID id, String value) { var a = new ExerciseSubmission(); a.setExerciseId(id); a.setAnswer(value); return a; }
    AssignmentSubmitRequest submit(String value) { var r = new AssignmentSubmitRequest(); r.setRevision(session.getDraftRevision()); r.setAnswers(List.of(answer(first.getExerciseId(),value))); return r; }

    @Test void recoveredDraftRetainsCurrentAnswerPositionAndOriginalClock() {
        var d = draft(); d.setCurrentIndex(1); d.setAnswers(List.of(answer(first.getExerciseId(),"first")));
        service.saveDraft(session.getSessionId(),d,student.getEmail());
        var recovered = service.recoverSession(session.getSessionId(),student.getEmail());
        assertEquals("saved apple",recovered.draft().getCurrentAnswer()); assertEquals(1,recovered.draft().getCurrentIndex());
        assertEquals(1,recovered.revision()); assertEquals(now.minusSeconds(60),recovered.sessionData().getStartedAt());
        assertEquals(session.getSessionId(), recovered.assignmentDetails().getOngoingSessionId());
        assertEquals(now.plusSeconds(540),recovered.sessionData().getTiming().deadline());
    }
    @Test void randomSubsetAndOrderStayFixedAcrossResume() {
        assignment.setRandomized(true); assignment.setGenerationMode("RANDOM_SUBSET"); assignment.setQuestionCount(1);
        var original = service.startAssignment(assignment.getAssignmentId(),student.getEmail());
        for(int i=0;i<10;i++) assertEquals(original.getExercises(),service.startAssignment(assignment.getAssignmentId(),student.getEmail()).getExercises());
        assertEquals(original.getExercises(),service.recoverSession(session.getSessionId(),student.getEmail()).sessionData().getExercises());
    }
    @Test void staleTabCannotOverwriteNewerDraft() {
        var old = draft(); service.saveDraft(session.getSessionId(),old,student.getEmail());
        String saved = session.getDraftState();
        old.setCurrentAnswer("stale overwrite");
        assertThrows(ConflictException.class,()->service.saveDraft(session.getSessionId(),old,student.getEmail()));
        assertEquals(saved,session.getDraftState());
        var staleSubmit = submit("stale final"); staleSubmit.setRevision(0L);
        assertThrows(ConflictException.class,()->service.submitAssignment(session.getSessionId(),staleSubmit,student.getEmail()));
        assertNull(session.getFinishedAt());
    }
    @Test void exactlyAtDeadlineNewPayloadIsIgnoredAndOnlySavedInputIsEvaluated() {
        service.saveDraft(session.getSessionId(),draft(),student.getEmail());
        when(clock.instant()).thenReturn(now.plusSeconds(540));
        service.submitAssignment(session.getSessionId(),submit("late tampered"),student.getEmail());
        var request = ArgumentCaptor.forClass(LessonSubmitRequest.class);
        verify(evaluationService).calculateEvaluationDetails(eq(List.of(first,second)),request.capture(),any());
        assertEquals("saved apple",request.getValue().getAnswers().getFirst().getAnswer());
        assertEquals(now.plusSeconds(540),session.getFinishedAt());
    }
    @Test void lateDraftCannotReplacePreviouslyAcceptedSnapshot() {
        service.saveDraft(session.getSessionId(),draft(),student.getEmail()); String before = session.getDraftState();
        when(clock.instant()).thenReturn(now.plusSeconds(600));
        var late = draft(); late.setCurrentAnswer("too late");
        assertTrue(service.saveDraft(session.getSessionId(),late,student.getEmail()).timing().finished());
        assertEquals(before,session.getDraftState()); assertEquals(1,session.getDraftRevision());
    }
    @Test void allowedLateSubmissionAcceptsNewAnswersAndRetainsActualFinishTime() {
        assignment.setAllowLateSubmission(true);
        service.saveDraft(session.getSessionId(),draft(),student.getEmail());
        when(clock.instant()).thenReturn(now.plusSeconds(1000));
        service.submitAssignment(session.getSessionId(),submit("allowed late"),student.getEmail());
        assertEquals(now.plusSeconds(1000),session.getFinishedAt());
        var request = ArgumentCaptor.forClass(LessonSubmitRequest.class);
        verify(evaluationService).calculateEvaluationDetails(any(),request.capture(),any());
        assertEquals("allowed late",request.getValue().getAnswers().getFirst().getAnswer());
    }
    @Test void repeatedFinalSubmissionAndBackgroundExpiryHaveOnlyOneEffect() {
        service.submitAssignment(session.getSessionId(),submit("apple"),student.getEmail());
        service.submitAssignment(session.getSessionId(),submit("different"),student.getEmail());
        service.closeExpiredSession(session.getSessionId());
        verify(evaluationService,times(1)).calculateEvaluationDetails(any(),any(),any());
        verify(streakService,times(1)).updateActivity(student);
        verify(sseService,times(1)).sendPing("teacher@example.test");
    }
    @Test void unrelatedAndDuplicateExercisesAreRejectedBeforeEvaluation() {
        var r = submit("apple"); r.setAnswers(List.of(answer(UUID.randomUUID(),"injected")));
        assertThrows(BadRequestException.class,()->service.submitAssignment(session.getSessionId(),r,student.getEmail()));
        r.setAnswers(List.of(answer(first.getExerciseId(),"a"),answer(first.getExerciseId(),"b")));
        assertThrows(BadRequestException.class,()->service.submitAssignment(session.getSessionId(),r,student.getEmail()));
        verifyNoInteractions(evaluationService);
    }
    @Test void anotherAccountCannotReadOrWriteDraftAndRemovedMemberCannotResume() {
        assertThrows(BadRequestException.class,()->service.recoverSession(session.getSessionId(),"stranger@example.test"));
        assertThrows(BadRequestException.class,()->service.saveDraft(session.getSessionId(),draft(),"teacher@example.test"));
        member.setStatus(MembershipStatus.PENDING);
        assertThrows(BadRequestException.class,()->service.recoverSession(session.getSessionId(),student.getEmail()));
    }
    @Test void backgroundExpiryFinalizesWithoutAnOpenBrowserAndWithoutLateChanges() {
        service.saveDraft(session.getSessionId(),draft(),student.getEmail());
        when(clock.instant()).thenReturn(now.plusSeconds(600));
        service.closeExpiredSession(session.getSessionId());
        assertEquals(now.plusSeconds(540),session.getFinishedAt());
        assertTrue(session.getRawAnswers().contains("saved apple"));
    }

    @Test void readingALegacyCompletedAttemptNeverChangesItsTimingOrAllocatesNewQuestions() {
        session.setFinishedAt(now.minusSeconds(10));
        assertTrue(service.recoverSession(session.getSessionId(),student.getEmail()).sessionData().getExercises().isEmpty());
        assertFalse(session.isTimingInitialized());assertNull(session.getAllocatedExerciseIds());
        verifyNoInteractions(evaluationService,streakService,sseService);
    }
}
