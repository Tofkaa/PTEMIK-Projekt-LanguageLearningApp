
package com.languageapp.backend.repository;

import com.languageapp.backend.entity.ClassroomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassroomMemberRepository extends JpaRepository<ClassroomMember, UUID> {
    List<ClassroomMember> findByClassroomClassroomId(UUID classroomId);
    List<ClassroomMember> findByUserUserId(UUID userId);
    Optional<ClassroomMember> findByClassroomClassroomIdAndUserUserId(UUID classroomId, UUID userId);
}
