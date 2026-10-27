package com.languageapp.backend.service;

import com.languageapp.backend.dto.request.TeacherGradeRequest;
import com.languageapp.backend.entity.*;
import com.languageapp.backend.exception.*;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherGradingTest {
    @Mock AssignmentSessionRepository sessions;
    @Mock ClassroomAssignmentRepository assignments;
    @Mock SseService sse;
    @InjectMocks AssignmentService service;
    AssignmentSession session;
    @BeforeEach void setup() {
        var teacher=new User();teacher.setEmail("teacher@example.test");
        var student=new User();student.setEmail("student@example.test");
        var room=new Classroom();room.setTeacher(teacher);
        var task=new ClassroomAssignment();task.setAssignmentId(UUID.randomUUID());task.setClassroom(room);
        session=new AssignmentSession();session.setSessionId(UUID.randomUUID());session.setAssignment(task);session.setUser(student);
        session.setFinishedAt(Instant.parse("2026-10-26T12:00:00Z"));session.setDraftRevision(3);
        lenient().when(sessions.findAssignmentId(session.getSessionId())).thenReturn(Optional.of(task.getAssignmentId()));
        lenient().when(assignments.findLockedById(task.getAssignmentId())).thenReturn(Optional.of(task));
        lenient().when(sessions.findById(session.getSessionId())).thenReturn(Optional.of(session));
    }
    TeacherGradeRequest request(int score,long revision) {
        var request=new TeacherGradeRequest();request.setTeacherScore(score);request.setRevision(revision);request.setTeacherComment("Feedback");return request;
    }
    @ParameterizedTest @ValueSource(ints={-1,101}) void rejectsInvalidScoreBeforeLocking(int score) {
        assertThrows(BadRequestException.class,()->service.gradeSession(session.getSessionId(),request(score,3),"teacher@example.test"));
        verifyNoInteractions(sessions,assignments,sse);
    }
    @Test void rejectsOversizedCommentAndMissingRevision() {
        var request=request(70,3);request.setTeacherComment("x".repeat(4001));
        assertThrows(BadRequestException.class,()->service.gradeSession(session.getSessionId(),request,"teacher@example.test"));
        request.setTeacherComment("");request.setRevision(null);
        assertThrows(BadRequestException.class,()->service.gradeSession(session.getSessionId(),request,"teacher@example.test"));
        verifyNoInteractions(sessions,assignments,sse);
    }
    @Test void unfinishedAndForeignSessionsCannotBeGraded() {
        session.setFinishedAt(null);
        assertThrows(ConflictException.class,()->service.gradeSession(session.getSessionId(),request(70,3),"teacher@example.test"));
        assertThrows(ForbiddenException.class,()->service.gradeSession(session.getSessionId(),request(70,3),"outsider@example.test"));
        verify(sessions,never()).save(any());verifyNoInteractions(sse);
    }
    @Test void staleGradeIsRejectedAndExplicitlyReloadedVersionCanBePublished() {
        assertEquals(4,service.gradeSession(session.getSessionId(),request(70,3),"teacher@example.test"));
        assertThrows(ConflictException.class,()->service.gradeSession(session.getSessionId(),request(80,3),"teacher@example.test"));
        assertEquals(70,session.getTeacherScore());
        assertEquals(5,service.gradeSession(session.getSessionId(),request(80,4),"teacher@example.test"));
        assertEquals(80,session.getTeacherScore());verify(sse,times(2)).sendPing("student@example.test");
    }
    @Test void notificationFailureDoesNotTurnASavedGradeIntoAnApplicationError() {
        doThrow(new IllegalStateException("synthetic transport failure")).when(sse).sendPing(anyString());
        assertEquals(4,service.gradeSession(session.getSessionId(),request(100,3),"teacher@example.test"));
        assertTrue(session.isGraded());assertEquals(100,session.getTeacherScore());
    }
}
