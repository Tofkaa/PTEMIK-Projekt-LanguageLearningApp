package com.languageapp.backend.repository;

import com.languageapp.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);


    boolean existsByFriendCode(String friendCode);
    boolean existsByNameAndUserTag(String name, String userTag);

    List<User> findTop50ByOrderByXpDesc();
    List<User> findTop50ByOrderByStreakDesc();

    Optional<User> findByFriendCode(String friendCode);
    Optional<User> findByNameAndUserTag(String name, String userTag);


    @Query(value = "SELECT * FROM users ORDER BY created_at DESC", nativeQuery = true)
    List<User> findAllUsersIncludingDeleted();

    @Modifying
    @Query(value = "UPDATE users SET is_active = :status WHERE user_id = :userId", nativeQuery = true)
    void updateUserStatus(@Param("userId") java.util.UUID userId, @Param("status") boolean status);

    /**
     * Bulk update operation for the Scheduled Job.
     * Resets the streak to 0 for all users whose last activity was before yesterday.
     */
    @Modifying
    @Query("UPDATE User u SET u.streak = 0 WHERE u.streak > 0 AND (u.lastActivityDate IS NULL OR u.lastActivityDate < :yesterday)")
    int resetExpiredStreaks(@Param("yesterday") LocalDate yesterday);

}