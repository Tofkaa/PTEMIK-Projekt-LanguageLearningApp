
package com.languageapp.backend.repository;

import com.languageapp.backend.entity.ClassroomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassroomMemberRepository extends JpaRepository<ClassroomMember, UUID> {
    List<ClassroomMember> findByClassroomClassroomId(UUID classroomId);
    List<ClassroomMember> findByUserUserId(UUID userId);
    Optional<ClassroomMember> findByClassroomClassroomIdAndUserUserId(UUID classroomId, UUID userId);

    @Query("SELECT u.name AS studentName, COUNT(p.progressId) AS totalCompletedLessons, AVG(p.highestScore) AS averageScore " +
            "FROM ClassroomMember cm " +
            "JOIN cm.user u " +
            "LEFT JOIN Progress p ON p.user.userId = u.userId AND p.isCompleted = true " +
            "WHERE cm.classroom.classroomId = :classroomId " +
            "GROUP BY u.userId, u.name")
    List<com.languageapp.backend.dto.projection.ClassroomMemberStatDTO> getClassroomStats(@org.springframework.data.repository.query.Param("classroomId") UUID classroomId);
}
