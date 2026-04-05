package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Challenge;
import com.languageapp.backend.enums.ChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {
    List<Challenge> findByChallengerUserIdOrOpponentUserId(UUID challengerId, UUID opponentId);
    List<Challenge> findByOpponentUserIdAndStatus(UUID opponentId, ChallengeStatus status);

    /**
     * Looks up expired challenges.
     */
    List<Challenge> findByStatusInAndEndTimeBefore(List<ChallengeStatus> statuses, java.time.LocalDateTime time);

    int countByOpponentUserIdAndStatus(UUID opponentId, ChallengeStatus status);
    @Query("SELECT COUNT(c) FROM Challenge c WHERE (c.challenger.userId = :userId OR c.opponent.userId = :userId) AND c.status IN (com.languageapp.backend.enums.ChallengeStatus.COMPLETED, com.languageapp.backend.enums.ChallengeStatus.DECLINED, com.languageapp.backend.enums.ChallengeStatus.EXPIRED)")
    int countHistoryForUser(@Param("userId") UUID userId);
}