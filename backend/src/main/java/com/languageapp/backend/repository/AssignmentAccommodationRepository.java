package com.languageapp.backend.repository;

import com.languageapp.backend.entity.AssignmentAccommodation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentAccommodationRepository extends JpaRepository<AssignmentAccommodation, UUID> {
    Optional<AssignmentAccommodation> findByAssignment_AssignmentIdAndUser_UserId(UUID assignmentId, UUID userId);
}
