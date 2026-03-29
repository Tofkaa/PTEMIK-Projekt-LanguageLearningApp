package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Result;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResultRepository extends JpaRepository<Result, UUID> {
    List<Result> findByUserUserId(UUID userId);
    List<Result> findByChallengeChallengeId(UUID challengeId);
    /**
     * Retrieves the 5 most recent lesson results for a specific user to calculate dynamic difficulty.
     */
    List<Result> findTop5ByUserUserIdOrderBySubmittedAtDesc(UUID userId);

    /**
     * Retrieves the 3 most recent lesson results for a specific user to show recent results on profile page.
     */
    @EntityGraph(attributePaths = {"lesson"})
    List<Result> findTop3ByUserUserIdOrderBySubmittedAtDesc(UUID userId);

    /**
     * Retrieves the absolute latest lesson result submitted by the user.
     * * CRITICAL FOR GAMIFICATION: Used by the EvaluationService to determine if the user
     * maintained their daily learning streak (comparing this date to LocalDate.now()).
     */
    Optional<Result> findFirstByUserUserIdOrderBySubmittedAtDesc(UUID userId);
}