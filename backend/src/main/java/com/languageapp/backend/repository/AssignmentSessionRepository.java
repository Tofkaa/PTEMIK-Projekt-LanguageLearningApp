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

    interface StudentStatistics {
        UUID getUserId();
        long getCompletedCount();
        double getAverageScore();
        java.time.Instant getLastActivity();
    }

    @Query("select s.user.userId as userId, count(distinct a.assignmentId) as completedCount, " +
            "avg(coalesce(s.teacherScore, s.finalScore, 0)) as averageScore, max(s.finishedAt) as lastActivity " +
            "from AssignmentSession s join s.assignment a where a.classroom.classroomId = :classroomId " +
            "and s.finishedAt is not null and exists (select m.classroomMemberId from ClassroomMember m " +
            "where m.classroom = a.classroom and m.user = s.user " +
            "and m.status = com.languageapp.backend.enums.MembershipStatus.ACCEPTED) group by s.user.userId")
    List<StudentStatistics> aggregateClassroomStatistics(@Param("classroomId") UUID classroomId);

    interface BestAnalyticsSession {
        UUID getSessionId();
        UUID getUserId();
        UUID getAssignmentId();
        int getScore();
        String getRawAnswers();
    }

    // PostgreSQL DISTINCT ON selects one best attempt BEFORE transferring any answer JSON.
    // Equal scores are stable: earliest start, then session UUID. All joins retain classroom ownership.
    @Query(value = """
            select distinct on (s.user_id, s.assignment_id)
                s.session_id as "sessionId", s.user_id as "userId", s.assignment_id as "assignmentId",
                coalesce(s.teacher_score, s.final_score, 0) as score, s.raw_answers as "rawAnswers"
            from assignment_sessions s
            join classroom_assignments a on a.assignment_id = s.assignment_id and a.is_active = true
            join classroom_members m on m.classroom_id = a.classroom_id and m.user_id = s.user_id and m.status = 'ACCEPTED'
            where a.classroom_id = :classroomId and s.finished_at is not null
            order by s.user_id, s.assignment_id, coalesce(s.teacher_score, s.final_score, 0) desc, s.started_at, s.session_id
            """, nativeQuery = true)
    List<BestAnalyticsSession> findBestClassroomAnalyticsSessions(@Param("classroomId") UUID classroomId);
}
