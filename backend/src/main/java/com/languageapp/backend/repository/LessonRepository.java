package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByTopicTopicId(UUID topicId);

    @Query("SELECT l.title AS lessonTitle, l.difficulty AS difficulty, COUNT(r.resultId) AS completionCount, AVG(r.timeTaken) AS averageTimeTaken " +
            "FROM Lesson l " +
            "LEFT JOIN Result r ON r.lesson.lessonId = l.lessonId " +
            "GROUP BY l.lessonId, l.title, l.difficulty")
    List<com.languageapp.backend.dto.projection.LessonPerformanceDTO> getLessonPerformanceStats();
}