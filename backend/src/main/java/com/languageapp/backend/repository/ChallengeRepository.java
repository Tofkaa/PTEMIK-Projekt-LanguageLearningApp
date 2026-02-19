package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {
    List<Challenge> findByChallengerUserIdOrOpponentUserId(UUID challengerId, UUID opponentId);
    List<Challenge> findByOpponentUserIdAndStatus(UUID opponentId, String status);
}