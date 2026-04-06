package com.languageapp.backend.repository;

import com.languageapp.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u.name AS name, u.xp AS xp, u.streak AS streak " +
            "FROM User u ORDER BY u.xp DESC")
    boolean existsByFriendCode(String friendCode);
    boolean existsByNameAndUserTag(String name, String userTag);

    List<User> findTop50ByOrderByXpDesc();
    List<User> findTop50ByOrderByStreakDesc();

    Optional<User> findByFriendCode(String friendCode);
    Optional<User> findByNameAndUserTag(String name, String userTag);
}