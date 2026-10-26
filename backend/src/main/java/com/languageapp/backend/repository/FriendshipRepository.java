package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Friendship;
import com.languageapp.backend.entity.User;
import com.languageapp.backend.enums.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    /**
     * Retrieves all friend requests received by a specific user with a given status (e.g., PENDING).
     */
    List<Friendship> findByFriendUserIdAndStatus(UUID friendId, FriendshipStatus status);

    /**
     * Retrieves a specific friendship connection between two users, regardless of direction.
     */
    Optional<Friendship> findByUserUserIdAndFriendUserId(UUID userId, UUID friendId);

    /**
     * Retrieves all accepted friendships for a user.
     * Checks both 'user' (sender) and 'friend' (receiver) columns to ensure a bidirectional relationship.
     */
    @Query("SELECT f FROM Friendship f WHERE (f.user.userId = :userId OR f.friend.userId = :userId) AND f.status = :status")
    List<Friendship> findAcceptedFriendships(@Param("userId") UUID userId, @Param("status") FriendshipStatus status);

    /**
     * Security check: Verifies if a relationship (either PENDING or ACCEPTED) already exists
     * between two users to prevent duplicate requests and spamming.
     */
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
            "WHERE (f.user = :user AND f.friend = :friend) " +
            "OR (f.user = :friend AND f.friend = :user)")
    boolean existsFriendshipBetween(@Param("user") User user, @Param("friend") User friend);

    int countByFriendUserIdAndStatus(UUID friendId, FriendshipStatus status);

    int countByUserUserIdAndStatus(UUID userId, FriendshipStatus friendshipStatus);

    void deleteByUserUserIdOrFriendUserId(UUID userId1, UUID userId2);
    /** PostgreSQL transaction lock: both directions of a pair use the same key. */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(:pair, 0))", nativeQuery = true)
    Integer lockConnectionPair(@Param("pair") String pair);

    @Query("select f from Friendship f where (f.user.userId=:a and f.friend.userId=:b) or (f.user.userId=:b and f.friend.userId=:a)")
    List<Friendship> findConnectionsBetween(@Param("a") UUID a, @Param("b") UUID b);

}