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

    private final SseService sseService;

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

        int pendingFriends = friendshipRepository.countByFriendUserIdAndStatus(userId, com.languageapp.backend.enums.FriendshipStatus.PENDING);
        int pendingChallenges = challengeRepository.countByOpponentUserIdAndStatus(userId, com.languageapp.backend.enums.ChallengeStatus.PENDING);
        int totalFriends = friendshipRepository.countByFriendUserIdAndStatus(userId, com.languageapp.backend.enums.FriendshipStatus.ACCEPTED)
                + friendshipRepository.countByUserUserIdAndStatus(userId, com.languageapp.backend.enums.FriendshipStatus.ACCEPTED);
        int totalHistory = challengeRepository.countHistoryForUser(userId);

        int tPending = 0;
        int tUngraded = 0;
        java.util.List<String> sActiveIds = new java.util.ArrayList<>();
        java.util.List<String> sGradedIds = new java.util.ArrayList<>();

        try {
            tPending = classroomMemberRepository.countByClassroom_Teacher_UserIdAndStatus(userId, com.languageapp.backend.enums.MembershipStatus.PENDING);
            tUngraded = sessionRepository.countByAssignment_Classroom_Teacher_UserIdAndIsGradedFalseAndFinishedAtIsNotNull(userId);

            // DIÁK 1: Új értékelések
            sessionRepository.findAll().stream()
                    .filter(s -> s.getUser().getUserId().equals(userId) && s.isGraded())
                    .forEach(s -> sGradedIds.add(s.getSessionId().toString()));

            // DIÁK 2: Aktív feladatok (CSAK AZT KÜLDI, AMIT MÉG NEM CSINÁLT MEG, ÉS NEM JÁRT LE!)
            classroomMemberRepository.findAllByUser_UserIdAndStatus(userId, com.languageapp.backend.enums.MembershipStatus.ACCEPTED)
                    .forEach(m -> {
                        assignmentRepository.findAllByClassroom_ClassroomIdOrderByCreatedAtDesc(m.getClassroom().getClassroomId())
                                .forEach(a -> {
                                    // Megnézzük, befejezte-e már a diák ezt a feladatot
                                    boolean hasFinished = sessionRepository.findAllByAssignment_AssignmentIdAndUser_UserId(a.getAssignmentId(), userId)
                                            .stream().anyMatch(session -> session.getFinishedAt() != null);

                                    // Megnézzük, lejárt-e a határidő
                                    boolean isExpired = a.getAvailableUntil() != null && a.getAvailableUntil().isBefore(java.time.LocalDateTime.now());

                                    // CSAK AKKOR kap pinget, ha még aktív és teendője van vele
                                    if (!hasFinished && !isExpired) {
                                        sActiveIds.add(a.getAssignmentId().toString());
                                    }
                                });
                    });

        } catch (Exception e) {
            System.err.println("Notification Summary Error: " + e.getMessage());
        }
        
        return ResponseEntity.ok(new NotificationSummaryDTO(
                pendingFriends, pendingChallenges, totalFriends, totalHistory,
                tPending, tUngraded, sActiveIds, sGradedIds, 1L
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