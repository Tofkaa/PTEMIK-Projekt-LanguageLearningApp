package com.languageapp.backend.repository;

import com.languageapp.backend.entity.ClassroomAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

/**
 * Repository for managing classroom assignments and their metadata.
 */
@Repository
public interface ClassroomAssignmentRepository extends JpaRepository<ClassroomAssignment, UUID> {

    /** Notification means an uncompleted assignment before its effective deadline, including scheduled work. */
    @Query("SELECT new com.languageapp.backend.dto.projection.AssignmentNotification(a.classroom.classroomId, a.assignmentId) " +
            "FROM ClassroomAssignment a JOIN ClassroomMember m ON m.classroom = a.classroom " +
            "LEFT JOIN AssignmentAccommodation ac ON ac.assignment = a AND ac.user.userId = :studentId " +
            "WHERE m.user.userId = :studentId AND m.status = com.languageapp.backend.enums.MembershipStatus.ACCEPTED " +
            "AND (COALESCE(ac.availableUntilOverride, a.availableUntil) IS NULL " +
            "OR COALESCE(ac.availableUntilOverride, a.availableUntil) > :now) " +
            "AND NOT EXISTS (SELECT s.sessionId FROM AssignmentSession s " +
            "WHERE s.assignment = a AND s.user.userId = :studentId AND s.finishedAt IS NOT NULL) " +
            "ORDER BY a.createdAt DESC, a.assignmentId")
    List<com.languageapp.backend.dto.projection.AssignmentNotification> findActiveNotificationReferences(
            @Param("studentId") UUID studentId, @Param("now") Instant now);

    /** Serializes timing edits, start and submission for an assignment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ClassroomAssignment a WHERE a.assignmentId = :id")
    Optional<ClassroomAssignment> findLockedById(@Param("id") UUID id);

    /**
     * Retrieves all assignments for a classroom, sorted by creation date.
     */
    List<ClassroomAssignment> findAllByClassroom_ClassroomIdOrderByCreatedAtDesc(UUID classroomId);

    /**
     * Retrieves currently active assignments for a student across all their classrooms.
     * Checks for valid membership and ensures the current time falls within the allowed window.
     *
     * @param userId The ID of the student
     * @param now The current instant; start is inclusive and end is exclusive
     * @return List of assignments that are currently open for submission
     */
    @Query("SELECT a FROM ClassroomAssignment a " +
            "JOIN ClassroomMember cm ON cm.classroom = a.classroom " +
            "LEFT JOIN AssignmentAccommodation ac ON ac.assignment = a AND ac.user.userId = :userId " +
            "WHERE cm.user.userId = :userId " +
            "AND cm.status = 'ACCEPTED' " +
            "AND (a.availableFrom IS NULL OR a.availableFrom <= :now) " +
            "AND (COALESCE(ac.availableUntilOverride, a.availableUntil) IS NULL OR COALESCE(ac.availableUntilOverride, a.availableUntil) > :now " +
            "OR (a.allowLateSubmission = true AND EXISTS (SELECT s.sessionId FROM AssignmentSession s WHERE s.assignment = a AND s.user.userId = :userId AND s.finishedAt IS NULL)))")
    List<ClassroomAssignment> findActiveAssignmentsForStudent(@Param("userId") UUID userId,
                                                           @Param("now") Instant now);
}
