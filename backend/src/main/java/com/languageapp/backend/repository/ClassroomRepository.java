package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {
    Optional<Classroom> findByInviteCode(String inviteCode);
    List<Classroom> findAllByTeacher_UserIdOrderByCreatedAtDesc(UUID teacherId);
    boolean existsByInviteCode(String inviteCode);

    @Query(value = "SELECT * FROM classrooms ORDER BY created_at DESC", nativeQuery = true)
    List<Classroom> findAllClassroomsIncludingDeleted();

    @Modifying
    @Query(value = "UPDATE classrooms SET is_active = :status WHERE classroom_id = :classroomId", nativeQuery = true)
    void updateClassroomStatus(@Param("classroomId") java.util.UUID classroomId, @Param("status") boolean status);
}