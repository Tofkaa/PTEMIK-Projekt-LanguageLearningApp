package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResultRepository extends JpaRepository<Result, UUID> {
    List<Result> findByUserUserId(UUID userId);
    List<Result> findByChallengeChallengeId(UUID challengeId);
}