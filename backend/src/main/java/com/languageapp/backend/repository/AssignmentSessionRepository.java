package com.languageapp.backend.repository;

import com.languageapp.backend.entity.AssignmentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing user-specific assignment sessions.
 * Tracks when students start and finish their tasks.
 */
@Repository
public interface AssignmentSessionRepository extends JpaRepository<AssignmentSession, UUID> {

    /**
     * Finds a session for a specific user and assignment.
     * Used to prevent multiple start attempts and to resume existing timers.
     */
    Optional<AssignmentSession> findByAssignment_AssignmentIdAndUser_UserId(UUID assignmentId, UUID userId);
}