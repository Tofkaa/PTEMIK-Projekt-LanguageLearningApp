package com.languageapp.backend.service;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.dto.projection.AssignmentAnswerReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassroomReportingTest {
    @Mock ClassroomAssignmentRepository assignments;
    @Mock ClassroomRepository classrooms;
    @Mock ClassroomMemberRepository members;
    @Mock AssignmentSessionRepository sessions;
    @InjectMocks AssignmentService service;
    UUID roomId=UUID.randomUUID(),studentId=UUID.randomUUID(),assignmentId=UUID.randomUUID();
    @BeforeEach void setup() {
        User teacher=new User();teacher.setEmail("owner@example.test");Classroom room=new Classroom();room.setTeacher(teacher);
        when(classrooms.findById(roomId)).thenReturn(Optional.of(room));
    }
    ClassroomMemberRepository.AcceptedStudent student(UUID id,String email) {
        var student=mock(ClassroomMemberRepository.AcceptedStudent.class);when(student.getUserId()).thenReturn(id);
        when(student.getName()).thenReturn(email);when(student.getEmail()).thenReturn(email);return student;
    }
    AssignmentSessionRepository.BestAnalyticsSession session(String raw) {
        var row=mock(AssignmentSessionRepository.BestAnalyticsSession.class);when(row.getSessionId()).thenReturn(UUID.randomUUID());
        when(row.getUserId()).thenReturn(studentId);when(row.getAssignmentId()).thenReturn(assignmentId);
        when(row.getScore()).thenReturn(75);when(row.getRawAnswers()).thenReturn(raw);return row;
    }
    void analytics(String raw) {
        var header=mock(ClassroomAssignmentRepository.AnalyticsHeader.class);
        when(header.getAssignmentId()).thenReturn(assignmentId);when(header.getTitle()).thenReturn("Assignment");
        when(assignments.findAnalyticsHeaders(roomId)).thenReturn(List.of(header));
        var student=student(studentId,"student@example.test");var attempt=session(raw);
        when(members.findAcceptedStudents(roomId)).thenReturn(List.of(student));
        when(sessions.findBestClassroomAnalyticsSessions(roomId)).thenReturn(List.of(attempt));
    }
    String wrong(UUID id,String correct) {
        return "{\"question\":\"Question\",\"correct\":false,\"exercise\":{\"exerciseId\":\""+id+"\"},\"serverCorrectAnswer\":"+(correct==null?"null":"\""+correct+"\"")+"}";
    }
    @Test void statisticsKeepRoundedStudentMeansPositiveOnlyClassMeanAndStudentsWithoutHistory() {
        UUID zero=UUID.randomUUID(),none=UUID.randomUUID();
        when(assignments.countByClassroom_ClassroomId(roomId)).thenReturn(4L);
        var one=student(studentId,"one");var second=student(zero,"zero");var third=student(none,"none");
        when(members.findAcceptedStudents(roomId)).thenReturn(List.of(one,second,third));
        var first=mock(AssignmentSessionRepository.StudentStatistics.class);when(first.getUserId()).thenReturn(studentId);
        when(first.getCompletedCount()).thenReturn(2L);when(first.getAverageScore()).thenReturn(66.666);
        when(first.getLastActivity()).thenReturn(java.time.Instant.parse("2026-10-27T12:00:00Z"));
        when(sessions.aggregateClassroomStatistics(roomId)).thenReturn(List.of(first));
        var result=service.getClassroomStatistics(roomId,"owner@example.test");
        assertEquals(4,result.getTotalAssignments());assertEquals(66.7,result.getClassAverage());
        assertEquals(2,result.getStudentProgress().getFirst().getCompletedCount());
        assertEquals("Nincs adat",result.getStudentProgress().getLast().getLastActivity());
        verify(sessions,never()).findAllByAssignment_AssignmentIdAndUser_UserId(any(),any());
    }
    @Test void snapshotAnswerWinsAndMalformedSessionsKeepTheirHeatmapScore() {
        UUID id=UUID.randomUUID();analytics("["+wrong(id,"Historical answer")+"]");
        var result=service.getClassroomAnalytics(roomId,"owner@example.test");
        assertEquals("Historical answer",result.getTopMistakes().getFirst().getServerCorrectAnswer());
        assertEquals(75,result.getHeatmapData().getFirst().getScores().get(assignmentId.toString()));
        verify(assignments,never()).findAnswerReferences(any(),any());
        var invalid=session("not valid JSON");
        when(sessions.findBestClassroomAnalyticsSessions(roomId)).thenReturn(List.of(invalid));
        assertTrue(service.getClassroomAnalytics(roomId,"owner@example.test").getTopMistakes().isEmpty());
    }
    @Test void fallbackIsAssignmentScopedAndSnapshotlessNullEntriesAreSafe() {
        UUID id=UUID.randomUUID(),foreign=UUID.randomUUID();analytics("[null,"+wrong(id,null)+","+wrong(foreign,null)+"]");
        when(assignments.findAnswerReferences(eq(roomId),anyList())).thenReturn(List.of(new AssignmentAnswerReference(assignmentId,id,Map.of("translation","Current fallback")),new AssignmentAnswerReference(UUID.randomUUID(),foreign,Map.of("answer","Must not leak"))));
        var result=service.getClassroomAnalytics(roomId,"owner@example.test");
        assertTrue(result.getTopMistakes().stream().anyMatch(m -> "Current fallback".equals(m.getServerCorrectAnswer())));
        assertTrue(result.getTopMistakes().stream().anyMatch(m -> m.getServerCorrectAnswer()==null));
        assertFalse(result.getTopMistakes().stream().anyMatch(m -> "Must not leak".equals(m.getServerCorrectAnswer())));
    }
    @Test void fallbackParameterBatchesAreBoundedAndDeduplicated() {
        var ids=new LinkedHashSet<UUID>();for(int i=0;i<501;i++)ids.add(UUID.randomUUID());
        analytics("["+String.join(",",ids.stream().map(id -> wrong(id,null)).toList())+","+wrong(ids.iterator().next(),null)+"]");
        service.getClassroomAnalytics(roomId,"owner@example.test");
        @SuppressWarnings("unchecked") ArgumentCaptor<List<UUID>> batches=ArgumentCaptor.forClass(List.class);
        verify(assignments,times(2)).findAnswerReferences(eq(roomId),batches.capture());
        assertEquals(Set.of(1,500),new HashSet<>(batches.getAllValues().stream().map(List::size).toList()));
        assertEquals(ids,new HashSet<>(batches.getAllValues().stream().flatMap(List::stream).toList()));
    }
}
