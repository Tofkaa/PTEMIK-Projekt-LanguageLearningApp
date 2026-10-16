package com.languageapp.backend.controller;

import com.languageapp.backend.dto.response.NotificationSummaryDTO;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.ChallengeStatus;
import com.languageapp.backend.enums.FriendshipStatus;
import com.languageapp.backend.enums.MembershipStatus;
import com.languageapp.backend.repository.*;
import com.languageapp.backend.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Instant;
import java.time.Clock;

/**
 * REST Controller handling real-time notifications and summary data fetching.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final ChallengeRepository challengeRepository;

    private final ClassroomAssignmentRepository assignmentRepository;
    private final AssignmentSessionRepository sessionRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final StudentVocabularyRepository studentVocabularyRepository;

    private final SseService sseService;
    private final Clock clock;

    /**
     * Aggregates and returns the user's notification state (Derived State).
     * Calculates pending requests and total statistics directly from the database without storing a separate 'is_read' flag.
     *
     * @param authentication The security context containing the authenticated user's details.
     * @return ResponseEntity containing the aggregated NotificationSummaryDTO.
     */
    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public ResponseEntity<NotificationSummaryDTO> getSummary(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        java.util.UUID userId = user.getUserId();
        Instant now = clock.instant();

        int pendingFriends = friendshipRepository.countByFriendUserIdAndStatus(userId, FriendshipStatus.PENDING);
        int pendingChallenges = challengeRepository.countByOpponentUserIdAndStatus(userId, ChallengeStatus.PENDING);
        int totalFriends = friendshipRepository.countByFriendUserIdAndStatus(userId, FriendshipStatus.ACCEPTED)
                + friendshipRepository.countByUserUserIdAndStatus(userId, FriendshipStatus.ACCEPTED);
        int totalHistory = challengeRepository.countHistoryForUser(userId);
        int dueVocabCount = (int) studentVocabularyRepository.countByUser_UserIdAndNextPracticeAtLessThanEqual(userId, clock.instant());

        java.util.List<String> tPendingIds = new java.util.ArrayList<>();
        java.util.List<String> tUngradedIds = new java.util.ArrayList<>();
        java.util.List<String> sActiveIds = new java.util.ArrayList<>();
        java.util.List<String> sGradedIds = new java.util.ArrayList<>();

        // 1. TANÁR: Várakozó diákok (classroomId:userId)
        classroomMemberRepository.findAll().stream()
                .filter(m -> m.getClassroom().getTeacher().getUserId().equals(userId) && m.getStatus() == MembershipStatus.PENDING)
                .forEach(m -> tPendingIds.add(m.getClassroom().getClassroomId() + ":" + m.getUser().getUserId()));

        // 2. TANÁR: Értékeletlen beadások SESSION szinten (classroomId:assignmentId:sessionId)
        sessionRepository.findAll().stream()
                .filter(s -> s.getAssignment().getClassroom().getTeacher().getUserId().equals(userId) && !s.isGraded() && s.getFinishedAt() != null)
                .forEach(s -> tUngradedIds.add(
                        s.getAssignment().getClassroom().getClassroomId() + ":" +
                                s.getAssignment().getAssignmentId() + ":" +
                                s.getSessionId()
                ));

        // 3. DIÁK: Új értékelések SESSION szinten (classroomId:assignmentId:sessionId)
        sessionRepository.findAll().stream()
                .filter(s -> s.getUser().getUserId().equals(userId) && s.isGraded())
                .forEach(s -> sGradedIds.add(
                        s.getAssignment().getClassroom().getClassroomId() + ":" +
                                s.getAssignment().getAssignmentId() + ":" +
                                s.getSessionId()
                ));

        // 4. DIÁK: Aktív feladatok (classroomId:assignmentId)
        classroomMemberRepository.findAllByUser_UserIdAndStatus(userId, MembershipStatus.ACCEPTED)
                .forEach(m -> {
                    assignmentRepository.findAllByClassroom_ClassroomIdOrderByCreatedAtDesc(m.getClassroom().getClassroomId())
                            .forEach(a -> {
                                boolean hasFinished = sessionRepository.findAllByAssignment_AssignmentIdAndUser_UserId(a.getAssignmentId(), userId)
                                        .stream().anyMatch(session -> session.getFinishedAt() != null);

                                boolean isExpired = a.getAvailableUntil() != null && !now.isBefore(a.getAvailableUntil());

                                if (!hasFinished && !isExpired) {
                                    sActiveIds.add(a.getClassroom().getClassroomId() + ":" + a.getAssignmentId());
                                }
                            });
                });

        return ResponseEntity.ok(new NotificationSummaryDTO(
                pendingFriends, pendingChallenges, totalFriends, totalHistory,
                tPendingIds, tUngradedIds, sActiveIds, sGradedIds,
                now.toEpochMilli(), dueVocabCount
        ));
    }

    /**
     * Establishes a unidirectional Server-Sent Events (SSE) stream for the authenticated user.
     *
     * @param authentication The security context containing the authenticated user's details.
     * @return SseEmitter object that holds the HTTP connection open.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        return sseService.subscribe(authentication.getName());
    }
}
