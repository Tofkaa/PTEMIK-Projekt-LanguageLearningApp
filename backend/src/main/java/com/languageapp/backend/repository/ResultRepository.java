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

    /**
     * Retrieves all results associated with a specific challenge.
     */
    List<Result> findByChallengeChallengeId(UUID challengeId);

    long countByUserUserId(UUID userId);

    List<Result> findTop5ByUserUserIdOrderBySubmittedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"lesson"})
    List<Result> findTop3ByUserUserIdOrderBySubmittedAtDesc(UUID userId);

    Optional<Result> findFirstByUserUserIdOrderBySubmittedAtDesc(UUID userId);

    /**
     * Retrieves a specific user's result for a given challenge.
     * Crucial for the evaluation engine to compare scores between challenger and opponent.
     *
     * @param challengeId The UUID of the challenge.
     * @param userId The UUID of the user.
     * @return An Optional containing the Result if found.
     */
    Optional<Result> findByChallengeChallengeIdAndUserUserId(UUID challengeId, UUID userId);
}