package com.languageapp.backend.controller;

import com.languageapp.backend.entity.*;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.service.SseService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.when;

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

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    void assignmentNotification_ShouldExpireExactlyAtDeadline(long secondsUntilDeadline) {
        User student = new User();
        student.setUserId(UUID.randomUUID());
        student.setXp(525);
        Classroom classroom = new Classroom();
        classroom.setClassroomId(UUID.randomUUID());
        ClassroomMember membership = new ClassroomMember();
        membership.setClassroom(classroom);
        ClassroomAssignment assignment = new ClassroomAssignment();
        assignment.setAssignmentId(UUID.randomUUID());
        assignment.setClassroom(classroom);
        assignment.setAvailableUntil(NOW.plusSeconds(secondsUntilDeadline));
        when(authentication.getName()).thenReturn("student@test.com");
        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(student));
        when(classroomMemberRepository.findAllByUser_UserIdAndStatus(student.getUserId(), MembershipStatus.ACCEPTED))
                .thenReturn(List.of(membership));
        when(assignmentRepository.findAllByClassroom_ClassroomIdOrderByCreatedAtDesc(classroom.getClassroomId()))
                .thenReturn(List.of(assignment));

        var summary = controller.getSummary(authentication).getBody();

        assertNotNull(summary);
        assertEquals(525, summary.getCurrentXp());
        assertEquals(secondsUntilDeadline > 0 ? 1 : 0, summary.getStudentActiveAssignmentIds().size());
    }
}
