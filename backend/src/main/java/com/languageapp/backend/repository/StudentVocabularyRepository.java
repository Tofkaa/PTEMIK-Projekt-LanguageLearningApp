package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Course;
import com.languageapp.backend.entity.StudentVocabulary;
import com.languageapp.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing student vocabulary entries.
 */
@Repository
public interface StudentVocabularyRepository extends JpaRepository<StudentVocabulary, UUID> {

    // --- GLOBÁLIS METÓDUSOK (AchievementService és Tesztek használják) ---

    long countByUser_UserId(UUID userId);

    long countByUser_UserIdAndSrsLevelGreaterThanEqual(UUID userId, int srsLevel);

    List<StudentVocabulary> findAllByUser(User user);


    // --- KURZUS-SPECIFIKUS METÓDUSOK (VocabularyService használja a többnyelvűséghez) ---

    /**
     * Retrieves the saved vocabulary for a specific user and course, ordered by acquisition date.
     */
    List<StudentVocabulary> findAllByUser_UserIdAndCourse_CourseIdOrderByFirstSeenAtDesc(UUID userId, UUID courseId);

    /**
     * Checks for the existence of a word in a user's active course vocabulary to prevent duplicates.
     */
    Optional<StudentVocabulary> findByUser_UserIdAndCourse_CourseIdAndWordIgnoreCase(UUID userId, UUID courseId, String word);

    /**
     * Fetches vocabulary items scheduled for review up to the specified time for a specific course.
     */
    List<StudentVocabulary> findAllByUser_UserIdAndCourse_CourseIdAndNextPracticeAtBeforeOrderByNextPracticeAtAsc(UUID userId, UUID courseId, LocalDateTime time);

    /**
     * Retrieves all dictionary entries for the user filtered by their active course.
     */
    List<StudentVocabulary> findAllByUserAndCourse(User user, Course course);

    long countByUser_UserIdAndNextPracticeAtBefore(UUID userId, LocalDateTime time);
}