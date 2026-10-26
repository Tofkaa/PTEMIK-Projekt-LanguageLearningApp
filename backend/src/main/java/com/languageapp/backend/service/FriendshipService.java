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
    private final org.springframework.context.ApplicationEventPublisher events;

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

        ConnectionChecks.requireVerified(sender);
        // Resolve the target user using the hybrid smart search
        User target = findUserByIdentifier(targetIdentifier);

        ConnectionChecks.requireAvailableTarget(target);
        if (sender.getUserId().equals(target.getUserId()))
            throw new BadRequestException("Saját magadnak nem küldhetsz barátkérelmet.");
        lockPair(sender.getUserId(), target.getUserId());
        if (friendshipRepository.existsFriendshipBetween(sender, target))
            throw new BadRequestException("Már barátok vagytok, vagy egy kérelmetek jóváhagyásra vár.");
        createRequest(sender, target);
    }

    public enum RequestState { CREATED, OUTGOING_PENDING, INCOMING_PENDING, CONNECTED }

    /** Same transaction-scoped pair lock as manual-code requests; no automatic acceptance. */
    @Transactional
    public RequestState requestFromInvitation(User sender, User target) {
        ConnectionChecks.requireVerified(sender);
        ConnectionChecks.requireAvailableTarget(target);
        if (sender.getUserId().equals(target.getUserId()))
            throw new BadRequestException("Ez a saját meghívólinked.");
        lockPair(sender.getUserId(), target.getUserId());
        var existing = friendshipRepository.findConnectionsBetween(sender.getUserId(), target.getUserId());
        if (existing.stream().anyMatch(f -> f.getStatus() == FriendshipStatus.ACCEPTED)) return RequestState.CONNECTED;
        if (!existing.isEmpty()) return existing.getFirst().getUser().getUserId().equals(sender.getUserId())
                ? RequestState.OUTGOING_PENDING : RequestState.INCOMING_PENDING;
        createRequest(sender, target);
        return RequestState.CREATED;
    }

    private void lockPair(UUID a, UUID b) {
        String first = a.toString(), second = b.toString();
        friendshipRepository.lockConnectionPair(first.compareTo(second) < 0 ? first+":"+second : second+":"+first);
    }

    private void createRequest(User sender, User target) {
        Friendship request = new Friendship();
        request.setUser(sender); request.setFriend(target); request.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(request);
        sseService.sendPingAfterCommit(target.getEmail());
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

        ConnectionChecks.requireVerified(friendship.getFriend());
        ConnectionChecks.requireAvailableTarget(friendship.getUser());

        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new BadRequestException("This request has already been accepted.");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
        log.info("Friendship request {} ACCEPTED by user {}", friendshipId, receiverId);

        events.publishEvent(new SocialAchievementEvent(List.of(friendship.getUser().getUserId(), friendship.getFriend().getUserId())));
        sseService.sendPingAfterCommit(friendship.getUser().getEmail());
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