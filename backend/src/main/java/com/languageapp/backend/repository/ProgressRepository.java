package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Progress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgressRepository extends JpaRepository<Progress, UUID> {
    List<Progress> findByUserUserId(UUID userId);
    Optional<Progress> findByUserUserIdAndLessonLessonId(UUID userId, UUID lessonId);
}