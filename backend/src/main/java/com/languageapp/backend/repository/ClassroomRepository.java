package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {
    List<Classroom> findByTeacherUserId(UUID teacherId);
    Optional<Classroom> findByInviteCode(String inviteCode);
}