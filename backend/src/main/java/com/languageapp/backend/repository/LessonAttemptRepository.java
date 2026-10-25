package com.languageapp.backend.repository;

import com.languageapp.backend.entity.LessonAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface LessonAttemptRepository extends JpaRepository<LessonAttempt, UUID> {
    List<LessonAttempt> findByUserUserId(UUID userId);

    @Modifying
    @Query("delete from LessonAttempt a where a.user.userId = :id")
    void deleteByUserUserId(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LessonAttempt a where a.attemptId = :id")
    Optional<LessonAttempt> findForSubmission(@Param("id") UUID id);
}
