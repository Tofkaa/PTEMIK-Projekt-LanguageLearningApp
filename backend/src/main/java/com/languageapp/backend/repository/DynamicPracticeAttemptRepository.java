package com.languageapp.backend.repository;

import com.languageapp.backend.entity.DynamicPracticeAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DynamicPracticeAttemptRepository extends JpaRepository<DynamicPracticeAttempt,UUID> {
    Optional<DynamicPracticeAttempt> findFirstByUser_UserIdAndCourse_CourseIdAndCompletedAtIsNullOrderByStartedAtDesc(UUID userId,UUID courseId);
    List<DynamicPracticeAttempt> findByUserUserId(UUID userId);
    void deleteByUserUserId(UUID userId);
}
