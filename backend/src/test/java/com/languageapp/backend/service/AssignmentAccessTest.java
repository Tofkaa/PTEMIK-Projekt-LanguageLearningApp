package com.languageapp.backend.service;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.exception.ForbiddenException;
import com.languageapp.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentAccessTest {
    @Mock ClassroomAssignmentRepository assignments;
    @Mock ClassroomRepository classrooms;
    @Mock ExerciseRepository exercises;
    @Mock UserRepository users;
    @Mock ClassroomMemberRepository members;
    @Mock AssignmentSessionRepository sessions;
    @Mock AssignmentAccommodationRepository accommodations;
    @Mock EvaluationService evaluation;
    @Mock StreakService streak;
    @Mock Clock clock;
    @Mock SseService sse;
    @InjectMocks AssignmentService service;
    UUID classroomId, studentId;
    @BeforeEach void setup() {
        classroomId=UUID.randomUUID(); studentId=UUID.randomUUID();
        var teacher=new User(); teacher.setEmail("owner@example.test");
        var room=new Classroom();room.setClassroomId(classroomId);room.setTeacher(teacher);
        when(classrooms.findById(classroomId)).thenReturn(Optional.of(room));
    }
    void student() {
        var user=new User();user.setUserId(studentId);user.setEmail("student@example.test");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }
    @Test void outsiderCannotLoadAnEmptyClassroomListOrStatistics() {
        student();
        assertThrows(ForbiddenException.class,()->service.getAssignmentsByClassroom(classroomId,"student@example.test"));
        assertThrows(ForbiddenException.class,()->service.getStudentClassroomStats(classroomId,"student@example.test"));
        verifyNoInteractions(assignments,sessions);
    }
    @ParameterizedTest @EnumSource(value=MembershipStatus.class,names={"PENDING","REJECTED"})
    void pendingAndRejectedMembershipsDoNotGrantAccess(MembershipStatus state) {
        student();var member=new ClassroomMember();member.setStatus(state);
        when(members.findByClassroom_ClassroomIdAndUser_UserId(classroomId,studentId)).thenReturn(Optional.of(member));
        assertThrows(ForbiddenException.class,()->service.getAssignmentsByClassroom(classroomId,"student@example.test"));
        assertThrows(ForbiddenException.class,()->service.getStudentClassroomStats(classroomId,"student@example.test"));
        verifyNoInteractions(assignments,sessions);
    }
    @Test void onlyOwnerMayReadTheFullStatistics() {
        assertThrows(ForbiddenException.class,()->service.getClassroomStatistics(classroomId,"student@example.test"));
        assertThrows(ForbiddenException.class,()->service.getClassroomStatistics(classroomId,"other-teacher@example.test"));
        verifyNoInteractions(assignments,members,sessions);
    }
    @Test void acceptedMemberMayReadAssignmentsAndOwnAggregates() {
        student();var member=new ClassroomMember();member.setStatus(MembershipStatus.ACCEPTED);
        when(members.findByClassroom_ClassroomIdAndUser_UserId(classroomId,studentId)).thenReturn(Optional.of(member));
        assertTrue(service.getAssignmentsByClassroom(classroomId,"student@example.test").isEmpty());
        assertEquals(0,service.getStudentClassroomStats(classroomId,"student@example.test").get("completedCount"));
    }
    @Test void theOwnerCanReadTheFullStatisticsWithoutAMembershipRecord() {
        assertTrue(service.getClassroomStatistics(classroomId,"owner@example.test").getStudentProgress().isEmpty());
        verify(users,never()).findByEmail(anyString());
    }
}
