package com.languageapp.backend.repository;

import com.languageapp.backend.entity.AssignmentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing user-specific assignment sessions.
 * Tracks when students start and finish their tasks.
 */
@Repository
public interface AssignmentSessionRepository extends JpaRepository<AssignmentSession, UUID> {

    @Query("SELECT new com.languageapp.backend.dto.projection.SubmissionNotification(" +
            "s.assignment.classroom.classroomId, s.assignment.assignmentId, s.sessionId) " +
            "FROM AssignmentSession s WHERE s.assignment.classroom.teacher.userId = :teacherId " +
            "AND s.isGraded = false AND s.finishedAt IS NOT NULL ORDER BY s.startedAt DESC, s.sessionId")
    List<com.languageapp.backend.dto.projection.SubmissionNotification> findUngradedNotificationReferences(
            @Param("teacherId") UUID teacherId);

    @Query("SELECT new com.languageapp.backend.dto.projection.SubmissionNotification(" +
            "s.assignment.classroom.classroomId, s.assignment.assignmentId, s.sessionId) " +
            "FROM AssignmentSession s WHERE s.user.userId = :studentId AND s.isGraded = true " +
            "ORDER BY s.startedAt DESC, s.sessionId")
    List<com.languageapp.backend.dto.projection.SubmissionNotification> findGradedNotificationReferences(
            @Param("studentId") UUID studentId);

    @Query("SELECT s.sessionId FROM AssignmentSession s WHERE s.finishedAt IS NULL AND s.allocatedExerciseIds IS NOT NULL")
    List<UUID> findRecoverableOngoingIds();

    @Query("SELECT s.assignment.assignmentId FROM AssignmentSession s WHERE s.sessionId = :id")
    Optional<UUID> findAssignmentId(@Param("id") UUID id);

    /**
     * Finds a session for a specific user and assignment.
     * Used to prevent multiple start attempts and to resume existing timers.
     */
    List<AssignmentSession> findAllByAssignment_AssignmentIdAndUser_UserId(UUID assignmentId, UUID userId);

    /**
     * Lekéri egy adott feladat összes próbálkozását, a legújabb kezdéssel legelöl.
     */
    List<AssignmentSession> findAllByAssignment_AssignmentIdOrderByStartedAtDesc(UUID assignmentId);

    int countByAssignment_Classroom_Teacher_UserIdAndIsGradedFalseAndFinishedAtIsNotNull(UUID userId);
}
