package com.languageapp.backend.service;

import com.languageapp.backend.entity.Friendship;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.FriendshipStatus;
import com.languageapp.backend.exception.BadRequestException;
import com.languageapp.backend.repository.FriendshipRepository;
import com.languageapp.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock private FriendshipRepository friendshipRepository;
    @Mock private UserRepository userRepository;
    @Mock private SseService sseService;

    @InjectMocks
    private FriendshipService friendshipService;

    private User sender;
    private User target;
    private Friendship pendingRequest;

    @BeforeEach
    void setUp() {
        sender = new User();
        sender.setUserId(UUID.randomUUID());
        sender.setName("Sender");
        sender.setEmail("sender@test.com");

        target = new User();
        target.setUserId(UUID.randomUUID());
        target.setName("Target");
        target.setUserTag("1234");
        target.setFriendCode("ABC-123");
        target.setEmail("target@test.com");

        pendingRequest = new Friendship();
        pendingRequest.setFriendshipId(UUID.randomUUID());
        pendingRequest.setUser(sender);
        pendingRequest.setFriend(target);
        pendingRequest.setStatus(FriendshipStatus.PENDING);
    }

    // --- 1. TESZT: Jelölés Discord-stílusú tag alapján ---
    @Test
    void sendFriendRequest_WithDiscordTag_ShouldCreatePendingRequest() {
        // Arrange
        when(userRepository.findById(sender.getUserId())).thenReturn(Optional.of(sender));
        when(userRepository.findByNameAndUserTag("Target", "1234")).thenReturn(Optional.of(target));
        when(friendshipRepository.existsFriendshipBetween(sender, target)).thenReturn(false);

        // Act
        friendshipService.sendFriendRequest(sender.getUserId(), "Target#1234");

        // Assert
        ArgumentCaptor<Friendship> captor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository, times(1)).save(captor.capture());

        assertEquals(FriendshipStatus.PENDING, captor.getValue().getStatus());
        assertEquals(sender, captor.getValue().getUser());
        assertEquals(target, captor.getValue().getFriend());
        verify(sseService, times(1)).sendPing(target.getEmail());
    }

    // --- 2. TESZT: Biztonság - Saját magunk jelölése ---
    @Test
    void sendFriendRequest_ToSelf_ShouldThrowException() {
        // Arrange
        when(userRepository.findById(sender.getUserId())).thenReturn(Optional.of(sender));
        // A kereső a saját kódunkat találja meg
        when(userRepository.findByFriendCode("MY-CODE")).thenReturn(Optional.of(sender));

        // Act & Assert
        assertThrows(BadRequestException.class, () ->
                friendshipService.sendFriendRequest(sender.getUserId(), "MY-CODE")
        );

        verify(friendshipRepository, never()).save(any());
    }

    // --- 3. TESZT: Jelölés elfogadása (A címzett által) ---
    @Test
    void acceptRequest_AsReceiver_ShouldChangeStatusToAccepted() {
        // Arrange
        when(friendshipRepository.findById(pendingRequest.getFriendshipId())).thenReturn(Optional.of(pendingRequest));

        // Act (A target user ID-jával hívjuk meg)
        friendshipService.acceptRequest(pendingRequest.getFriendshipId(), target.getUserId());

        // Assert
        assertEquals(FriendshipStatus.ACCEPTED, pendingRequest.getStatus());
        verify(friendshipRepository, times(1)).save(pendingRequest);
        verify(sseService, times(1)).sendPing(sender.getEmail());
    }

    // --- 4. TESZT: Biztonság - Küldő próbálja elfogadni a saját kérését ---
    @Test
    void acceptRequest_AsSender_ShouldThrowSecurityException() {
        // Arrange
        when(friendshipRepository.findById(pendingRequest.getFriendshipId())).thenReturn(Optional.of(pendingRequest));

        // Act & Assert (A sender user ID-jával hívjuk meg a címzetté helyett)
        assertThrows(BadRequestException.class, () ->
                friendshipService.acceptRequest(pendingRequest.getFriendshipId(), sender.getUserId())
        );

        assertEquals(FriendshipStatus.PENDING, pendingRequest.getStatus()); // Változatlan marad
        verify(friendshipRepository, never()).save(any());
    }
}