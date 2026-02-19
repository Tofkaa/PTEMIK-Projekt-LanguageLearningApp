package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    List<Friendship> findByUserUserIdOrFriendUserId(UUID userId, UUID friendId);
    List<Friendship> findByFriendUserIdAndStatus(UUID friendId, String status);
    Optional<Friendship> findByUserUserIdAndFriendUserId(UUID userId, UUID friendId);
}