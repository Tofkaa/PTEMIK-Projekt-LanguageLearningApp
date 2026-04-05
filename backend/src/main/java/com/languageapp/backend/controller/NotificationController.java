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

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final ChallengeRepository challengeRepository;
    private final SseService sseService;

    // 1. VÉGPONT: Az adatok lekérése (Származtatott Állapot!)
    @GetMapping("/summary")
    public ResponseEntity<NotificationSummaryDTO> getSummary(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();

        int totalFriends = friendshipRepository.countByFriendUserIdAndStatus(user.getUserId(), FriendshipStatus.ACCEPTED)
                + friendshipRepository.countByUserUserIdAndStatus(user.getUserId(), FriendshipStatus.ACCEPTED); // Vagy ahogy a te logikád számolja a barátokat

        // Ahol te vagy bárhogyan résztvevő, és az állapot COMPLETED, EXPIRED vagy DECLINED
        int totalHistory = challengeRepository.countHistoryForUser(user.getUserId());

        // Számoljuk a meglévő táblákból (Séma módosítás nélkül!)
        int pendingFriends = friendshipRepository.countByFriendUserIdAndStatus(user.getUserId(), FriendshipStatus.PENDING);

        // Csak azokat számoljuk, ahol ENGEM hívtak ki (én vagyok az opponent) és PENDING
        int pendingChallenges = challengeRepository.countByOpponentUserIdAndStatus(user.getUserId(), ChallengeStatus.PENDING);

        return ResponseEntity.ok(new NotificationSummaryDTO(pendingFriends, pendingChallenges, totalFriends, totalHistory));
    }

    // 2. VÉGPONT: Rácsatlakozás az élő közvetítésre
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        return sseService.subscribe(authentication.getName());
    }
}