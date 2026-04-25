package com.languageapp.backend.service;

import com.languageapp.backend.dto.response.FriendDTO;
import com.languageapp.backend.dto.response.FriendRequestDTO;
import com.languageapp.backend.entity.Friendship;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.FriendshipStatus;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.FriendshipRepository;
import com.languageapp.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final SseService sseService;

    /**
     * Sends a friend request using either a Discord-style tag or a Steam-style friend code.
     *
     * @param senderId the UUID of the user sending the request
     * @param targetIdentifier the search string provided by the user (Name#Tag or FriendCode)
     */
    @Transactional
    public void sendFriendRequest(UUID senderId, String targetIdentifier) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BadRequestException("Sender not found."));

        // Resolve the target user using the hybrid smart search
        User target = findUserByIdentifier(targetIdentifier);

        // Security Validation 1: Prevent self-requests
        if (sender.getUserId().equals(target.getUserId())) {
            throw new BadRequestException("You cannot send a friend request to yourself.");
        }

        // Security Validation 2: Prevent duplicate requests or establishing multiple connections
        if (friendshipRepository.existsFriendshipBetween(sender, target)) {
            throw new BadRequestException("A connection or pending request already exists between you and this user.");
        }

        Friendship request = new Friendship();
        request.setUser(sender);
        request.setFriend(target);
        request.setStatus(FriendshipStatus.PENDING);

        friendshipRepository.save(request);
        log.info("Friend request successfully sent from {} to {}", sender.getEmail(), target.getEmail());

        sseService.sendPing(target.getEmail());
    }

    /**
     * Hybrid identifier resolver (Smart Search).
     * Determines whether the input is a Discord-style tag (Name#1234) or a Steam-style code (A7B-9X2).
     */
    private User findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BadRequestException("Identifier cannot be empty.");
        }

        String cleanIdentifier = identifier.trim();

        if (cleanIdentifier.contains("#")) {
            String[] parts = cleanIdentifier.split("#");
            if (parts.length != 2) {
                throw new BadRequestException("Invalid format. Please use the 'Name#1234' format.");
            }
            return userRepository.findByNameAndUserTag(parts[0].trim(), parts[1].trim())
                    .orElseThrow(() -> new BadRequestException("No user found with this name and tag."));
        } else {
            // Handle Friend Code format (e.g., A7B-9X2)
            return userRepository.findByFriendCode(cleanIdentifier.toUpperCase())
                    .orElseThrow(() -> new BadRequestException("No user found with this friend code."));
        }
    }

    /**
     * Retrieves all inbound, pending friend requests targeted at the specified user.
     */
    @Transactional(readOnly = true)
    public List<FriendRequestDTO> getPendingRequests(UUID receiverId) {
        return friendshipRepository.findByFriendUserIdAndStatus(receiverId, FriendshipStatus.PENDING)
                .stream()
                .map(friendship -> new FriendRequestDTO(
                        friendship.getFriendshipId(),
                        friendship.getUser().getName(),
                        friendship.getUser().getUserTag(),
                        friendship.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Accepts a pending friend request, establishing the connection.
     */
    @Transactional
    public void acceptRequest(UUID friendshipId, UUID receiverId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new BadRequestException("Friend request not found."));

        // Security check: Only the designated recipient can accept the request
        if (!friendship.getFriend().getUserId().equals(receiverId)) {
            throw new BadRequestException("You are not authorized to accept this request.");
        }

        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new BadRequestException("This request has already been accepted.");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
        log.info("Friendship request {} ACCEPTED by user {}", friendshipId, receiverId);

        sseService.sendPing(friendship.getUser().getEmail());
    }

    /**
     * Rejects a pending friend request by hard-deleting the record from the database.
     * This allows users to send a new request in the future if they change their minds.
     */
    @Transactional
    public void rejectRequest(UUID friendshipId, UUID receiverId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new BadRequestException("Friend request not found."));

        // Security check: Only the designated recipient can reject the request
        if (!friendship.getFriend().getUserId().equals(receiverId)) {
            throw new BadRequestException("You are not authorized to reject this request.");
        }

        friendshipRepository.delete(friendship);
        log.info("Friendship request {} REJECTED by user {}", friendshipId, receiverId);
    }

    /**
     * Removes an established friendship connection.
     * Ensures that only the participants of the friendship can delete it.
     *
     * @param friendshipId the unique ID of the friendship to remove
     * @param currentUserId the ID of the user requesting the removal
     */
    @Transactional
    public void removeFriend(UUID friendshipId, UUID currentUserId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new BadRequestException("Friendship connection not found."));

        // Security check: Only participants can delete the friendship
        if (!friendship.getUser().getUserId().equals(currentUserId) &&
                !friendship.getFriend().getUserId().equals(currentUserId)) {
            throw new BadRequestException("You are not authorized to remove this friendship.");
        }

        friendshipRepository.delete(friendship);
        log.info("Friendship {} REMOVED by user {}", friendshipId, currentUserId);
    }

    /**
     * Retrieves a list of all accepted friends for the specified user.
     */
    @Transactional(readOnly = true)
    public List<FriendDTO> getMyFriends(UUID currentUserId) {
        return friendshipRepository.findAcceptedFriendships(currentUserId, FriendshipStatus.ACCEPTED)
                .stream()
                .map(friendship -> {
                    // Determine which user in the bidirectional relationship is the "friend"
                    User otherUser = friendship.getUser().getUserId().equals(currentUserId)
                            ? friendship.getFriend()
                            : friendship.getUser();

                    return new FriendDTO(
                            friendship.getFriendshipId(),
                            otherUser.getUserId(),
                            otherUser.getName(),
                            otherUser.getUserTag(),
                            otherUser.getFriendCode()
                    );
                })
                .collect(Collectors.toList());
    }
}