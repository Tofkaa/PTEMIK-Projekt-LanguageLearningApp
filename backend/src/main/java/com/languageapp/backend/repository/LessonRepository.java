package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByTopicTopicId(UUID topicId);

    List<Lesson> findByTopic_Course_CourseIdOrderByTopic_TopicIdAscOrderIndexAsc(UUID courseId);

    List<Lesson> findByTopic_Course_CourseId(UUID courseId);

    /** Scalar dashboard metadata: exercises and their answer/content JSON are never loaded. */
    interface CourseLesson {
        UUID getLessonId();
        UUID getTopicId();
        String getTopicName();
        String getTitle();
        Integer getOrderIndex();
        String getLanguage();
        String getDescription();
    }

    interface AvailableLevel {
        UUID getLessonId();
        String getDifficulty();
    }

    @Query("select l.lessonId as lessonId, l.topic.topicId as topicId, l.topic.name as topicName, " +
            "l.title as title, l.orderIndex as orderIndex, l.language as language, l.description as description " +
            "from Lesson l where l.topic.course.courseId = :courseId and l.isActive = true and l.topic.isActive = true")
    List<CourseLesson> findCourseOverview(@Param("courseId") UUID courseId);

    @Query("select distinct e.lesson.lessonId as lessonId, e.difficulty as difficulty from Exercise e " +
            "where e.lesson.topic.course.courseId = :courseId and e.isActive = true " +
            "and e.lesson.isActive = true and e.lesson.topic.isActive = true")
    List<AvailableLevel> findAvailableCourseLevels(@Param("courseId") UUID courseId);
}