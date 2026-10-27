package com.languageapp.backend.entity;

import com.languageapp.backend.enums.FriendshipStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "friendships", indexes = {
        @Index(name = "idx_friends_sender_status", columnList = "user_id, status, friend_id"),
        @Index(name = "idx_friends_receiver_status", columnList = "friend_id, status, user_id")
})

public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "friendship_id", updatable = false, nullable = false)
    private UUID friendshipId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
