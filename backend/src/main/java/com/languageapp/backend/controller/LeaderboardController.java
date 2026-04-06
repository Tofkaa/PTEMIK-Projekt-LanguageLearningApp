package com.languageapp.backend.controller;

import com.languageapp.backend.dto.response.UserRankingDTO;
import com.languageapp.backend.entity.Friendship;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.FriendshipStatus;
import com.languageapp.backend.repository.FriendshipRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for handling leaderboard-related operations.
 * Provides endpoints for retrieving both global and friends-only user rankings.
 */
@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    /**
     * Retrieves the global leaderboard of the top 50 users.
     * The ranking can be sorted either by total XP or by daily streak.
     *
     * @param sortBy The sorting criteria ("xp" or "streak"). Defaults to "xp".
     * @return A list of {@link UserRankingDTO} containing the top users' ranking details.
     */
    @GetMapping("/global")
    public ResponseEntity<List<UserRankingDTO>> getGlobalLeaderboard(@RequestParam(defaultValue = "xp") String sortBy) {

        List<User> topUsers;
        if ("streak".equalsIgnoreCase(sortBy)) {
            topUsers = userRepository.findTop50ByOrderByStreakDesc();
        } else {
            topUsers = userRepository.findTop50ByOrderByXpDesc();
        }

        List<UserRankingDTO> leaderboard = new java.util.ArrayList<>();
        int currentRank = 1;
        for (User u : topUsers) {
            leaderboard.add(new UserRankingDTO(
                    u.getUserId(), u.getName(), u.getUserTag(), u.getXp(), u.getStreak(), currentRank++
            ));
        }

        return ResponseEntity.ok(leaderboard);
    }

    /**
     * Retrieves the leaderboard consisting of the currently authenticated user
     * and their accepted friends.
     *
     * @param authentication The current user's authentication details.
     * @param sortBy         The sorting criteria ("xp" or "streak"). Defaults to "xp".
     * @return A list of {@link UserRankingDTO} containing the user's and their friends' ranking details.
     */
    @GetMapping("/friends")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserRankingDTO>> getFriendsLeaderboard(
            Authentication authentication,
            @RequestParam(defaultValue = "xp") String sortBy) {

        User currentUser = userRepository.findByEmail(authentication.getName()).orElseThrow();

        List<Friendship> friendships = friendshipRepository.findAcceptedFriendships(currentUser.getUserId(), FriendshipStatus.ACCEPTED);

        List<User> leaderboardUsers = new java.util.ArrayList<>();
        leaderboardUsers.add(currentUser);

        for (Friendship f : friendships) {
            User friend = f.getUser().getUserId().equals(currentUser.getUserId()) ? f.getFriend() : f.getUser();
            leaderboardUsers.add(friend);
        }

        if ("streak".equalsIgnoreCase(sortBy)) {
            leaderboardUsers.sort((u1, u2) -> Integer.compare(u2.getStreak(), u1.getStreak()));
        } else {
            leaderboardUsers.sort((u1, u2) -> Integer.compare(u2.getXp(), u1.getXp()));
        }

        List<UserRankingDTO> leaderboard = new java.util.ArrayList<>();
        int currentRank = 1;
        for (User u : leaderboardUsers) {
            leaderboard.add(new UserRankingDTO(
                    u.getUserId(), u.getName(), u.getUserTag(), u.getXp(), u.getStreak(), currentRank++
            ));
        }

        return ResponseEntity.ok(leaderboard);
    }
}