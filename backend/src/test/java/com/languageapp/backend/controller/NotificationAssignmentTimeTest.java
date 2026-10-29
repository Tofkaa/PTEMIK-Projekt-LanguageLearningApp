package com.languageapp.backend.controller;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.dto.projection.*;
import com.languageapp.backend.enums.FriendshipStatus;
import com.languageapp.backend.enums.ChallengeStatus;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.service.SseService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationAssignmentTimeTest {
    @Mock private UserRepository userRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private ChallengeRepository challengeRepository;
    @Mock private ClassroomAssignmentRepository assignmentRepository;
    @Mock private AssignmentSessionRepository sessionRepository;
    @Mock private AssignmentAccommodationRepository accommodationRepository;
    @Mock private ClassroomMemberRepository classroomMemberRepository;
    @Mock private StudentVocabularyRepository studentVocabularyRepository;
    @Mock private SseService sseService;
    @Mock private Authentication authentication;
    private static final Instant NOW = Instant.parse("2026-10-16T16:00:00Z");
    @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    @InjectMocks private NotificationController controller;

    @Test
    void summaryUsesScopedProjectionsAndPreservesKeysCountersAndOneTimeSnapshot() {
        User student = new User();
        student.setUserId(UUID.randomUUID());
        student.setXp(525);
        Course course = new Course(); course.setCourseId(UUID.randomUUID()); student.setActiveCourse(course);
        UUID room=UUID.randomUUID(), assignment=UUID.randomUUID(), session=UUID.randomUUID(), applicant=UUID.randomUUID();
        when(authentication.getName()).thenReturn("student@test.com");
        when(userRepository.findNotificationUser("student@test.com")).thenReturn(Optional.of(new UserRepository.NotificationUser(student.getUserId(),student.getXp(),course.getCourseId(),"en",0)));
        when(classroomMemberRepository.findPendingNotificationReferences(student.getUserId()))
                .thenReturn(List.of(new PendingJoinNotification(room,applicant)));
        when(sessionRepository.findUngradedNotificationReferences(student.getUserId()))
                .thenReturn(List.of(new SubmissionNotification(room,assignment,session)));
        when(sessionRepository.findGradedNotificationReferences(student.getUserId()))
                .thenReturn(List.of(new SubmissionNotification(room,assignment,session)));
        when(assignmentRepository.findActiveNotificationReferences(student.getUserId(),NOW))
                .thenReturn(List.of(new AssignmentNotification(room,assignment)));
        when(friendshipRepository.countByFriendUserIdAndStatus(student.getUserId(),FriendshipStatus.PENDING)).thenReturn(2);
        when(friendshipRepository.countByFriendUserIdAndStatus(student.getUserId(),FriendshipStatus.ACCEPTED)).thenReturn(3);
        when(friendshipRepository.countByUserUserIdAndStatus(student.getUserId(),FriendshipStatus.ACCEPTED)).thenReturn(4);
        when(challengeRepository.countByOpponentUserIdAndStatus(student.getUserId(),ChallengeStatus.PENDING)).thenReturn(5);
        when(challengeRepository.countHistoryForUser(student.getUserId())).thenReturn(8);
        when(studentVocabularyRepository.countByUser_UserIdAndCourse_CourseIdAndNextPracticeAtLessThanEqual(student.getUserId(),course.getCourseId(),NOW)).thenReturn(9L);

        var summary = controller.getSummary(authentication).getBody();

        assertNotNull(summary);
        assertEquals(525, summary.getCurrentXp());
        assertEquals(List.of(room+":"+assignment),summary.getStudentActiveAssignmentIds());
        assertEquals(List.of(room+":"+applicant),summary.getTeacherPendingJoinRequestIds());
        assertEquals(List.of(room+":"+assignment+":"+session),summary.getTeacherUngradedSubmissionIds());
        assertEquals(List.of(room+":"+assignment+":"+session),summary.getStudentGradedSessionIds());
        assertEquals(2,summary.getPendingFriendRequests());assertEquals(5,summary.getPendingChallenges());
        assertEquals(7,summary.getTotalAcceptedFriends());assertEquals(8,summary.getTotalHistoryItems());
        assertEquals(9,summary.getDueVocabularyCount());assertEquals(9,summary.getTotal());
        assertEquals(NOW.toEpochMilli(),summary.getLastPingTime());
        verify(clock,times(1)).instant();
        verify(classroomMemberRepository,never()).findAll();verify(sessionRepository,never()).findAll();
        verify(assignmentRepository,never()).findAllByClassroom_ClassroomIdOrderByCreatedAtDesc(any());
        verifyNoInteractions(accommodationRepository);
    }
}
