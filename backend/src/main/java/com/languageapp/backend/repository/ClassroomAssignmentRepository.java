package com.languageapp.backend.repository;

import com.languageapp.backend.entity.ClassroomAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for managing classroom assignments and their metadata.
 */
@Repository
public interface ClassroomAssignmentRepository extends JpaRepository<ClassroomAssignment, UUID> {

    /**
     * Retrieves all assignments for a classroom, sorted by creation date.
     */
    List<ClassroomAssignment> findAllByClassroom_ClassroomIdOrderByCreatedAtDesc(UUID classroomId);

    /**
     * Retrieves currently active assignments for a student across all their classrooms.
     * Checks for valid membership and ensures the current time falls within the allowed window.
     *
     * @param userId The ID of the student
     * @return List of assignments that are currently open for submission
     */
    @Query("SELECT a FROM ClassroomAssignment a " +
            "JOIN ClassroomMember cm ON cm.classroom = a.classroom " +
            "WHERE cm.user.userId = :userId " +
            "AND cm.status = 'ACCEPTED' " +
            "AND (a.availableFrom IS NULL OR a.availableFrom <= CURRENT_TIMESTAMP) " +
            "AND (a.availableUntil IS NULL OR a.availableUntil >= CURRENT_TIMESTAMP)")
    List<ClassroomAssignment> findActiveAssignmentsForStudent(@Param("userId") UUID userId);
}