package com.languageapp.backend.controller;

import com.languageapp.backend.dto.response.NotificationSummaryDTO;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.ChallengeStatus;
import com.languageapp.backend.enums.FriendshipStatus;
import com.languageapp.backend.repository.ChallengeRepository;
import com.languageapp.backend.repository.FriendshipRepository;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    private final SseService sseService;

    /**
     * Aggregates and returns the user's notification state (Derived State).
     * Calculates pending requests and total statistics directly from the database without storing a separate 'is_read' flag.
     *
     * @param authentication The security context containing the authenticated user's details.
     * @return ResponseEntity containing the aggregated NotificationSummaryDTO.
     */
    @GetMapping("/summary")
    public ResponseEntity<NotificationSummaryDTO> getSummary(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();

        int totalFriends = friendshipRepository.countByFriendUserIdAndStatus(user.getUserId(), FriendshipStatus.ACCEPTED)
                + friendshipRepository.countByUserUserIdAndStatus(user.getUserId(), FriendshipStatus.ACCEPTED);

        int totalHistory = challengeRepository.countHistoryForUser(user.getUserId());
        int pendingFriends = friendshipRepository.countByFriendUserIdAndStatus(user.getUserId(), FriendshipStatus.PENDING);
        int pendingChallenges = challengeRepository.countByOpponentUserIdAndStatus(user.getUserId(), ChallengeStatus.PENDING);

        return ResponseEntity.ok(new NotificationSummaryDTO(pendingFriends, pendingChallenges, totalFriends, totalHistory));
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