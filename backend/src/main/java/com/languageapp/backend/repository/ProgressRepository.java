package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Progress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgressRepository extends JpaRepository<Progress, UUID> {
    List<Progress> findByUserUserId(UUID userId);

    @Query("select count(distinct p.lesson.lessonId) from Progress p where p.user.userId = :userId and p.isCompleted = true")
    long countCompletedLessons(@Param("userId") UUID userId);

    Optional<Progress> findByUserUserIdAndLessonLessonId(UUID userId, UUID lessonId);

    Optional<Progress> findByUserUserIdAndLessonLessonIdAndCompletedDifficulty(UUID userId, UUID lessonId, String difficulty);

    interface PassedLevel {
        UUID getLessonId();
        String getDifficulty();
    }

    @Query("select p.lesson.lessonId as lessonId, p.completedDifficulty as difficulty from Progress p " +
            "where p.user.userId = :userId and p.lesson.topic.course.courseId = :courseId and p.isCompleted = true")
    List<PassedLevel> findPassedCourseLevels(@Param("userId") UUID userId, @Param("courseId") UUID courseId);
}