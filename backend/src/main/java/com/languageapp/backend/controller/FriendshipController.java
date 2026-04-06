package com.languageapp.backend.controller;

import com.languageapp.backend.dto.response.FriendDTO;
import com.languageapp.backend.dto.response.FriendRequestDTO;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.UserRepository;
import com.languageapp.backend.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/friendships")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final UserRepository userRepository;

    /**
     * Endpoint to dispatch a new friend request using a Smart Search identifier.
     */
    @PostMapping("/request")
    public ResponseEntity<?> sendFriendRequest(
            @RequestBody Map<String, String> payload,
            Authentication authentication) {

        String senderEmail = authentication.getName();
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new BadRequestException("Authenticated user not found."));

        String targetIdentifier = payload.get("targetIdentifier");
        friendshipService.sendFriendRequest(sender.getUserId(), targetIdentifier);

        return ResponseEntity.ok(Map.of("message", "Barátkérelem sikeresen elküldve!"));
    }

    /**
     * Endpoint to retrieve all pending inbound friend requests for the authenticated user.
     */
    @GetMapping("/requests/pending")
    public ResponseEntity<List<FriendRequestDTO>> getPendingRequests(Authentication authentication) {
        User receiver = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new BadRequestException("Authenticated user not found."));

        List<FriendRequestDTO> pendingRequests = friendshipService.getPendingRequests(receiver.getUserId());
        return ResponseEntity.ok(pendingRequests);
    }

    /**
     * Endpoint to accept a specific friend request.
     */
    @PostMapping("/requests/{friendshipId}/accept")
    public ResponseEntity<?> acceptRequest(@PathVariable UUID friendshipId, Authentication authentication) {
        User receiver = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new BadRequestException("Authenticated user not found."));

        friendshipService.acceptRequest(friendshipId, receiver.getUserId());
        return ResponseEntity.ok(Map.of("message", "Barátkérelem elfogadva!"));
    }

    /**
     * Endpoint to decline/reject a specific friend request.
     */
    @PostMapping("/requests/{friendshipId}/reject")
    public ResponseEntity<?> rejectRequest(@PathVariable UUID friendshipId, Authentication authentication) {
        User receiver = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new BadRequestException("Authenticated user not found."));

        friendshipService.rejectRequest(friendshipId, receiver.getUserId());
        return ResponseEntity.ok(Map.of("message", "Barátkérelem elutasítva."));
    }

    /**
     * Endpoint to retrieve the authenticated user's accepted friends list.
     */
    @GetMapping("/accepted")
    public ResponseEntity<List<FriendDTO>> getMyFriends(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new BadRequestException("Authenticated user not found."));

        List<FriendDTO> friends = friendshipService.getMyFriends(user.getUserId());
        return ResponseEntity.ok(friends);
    }
}