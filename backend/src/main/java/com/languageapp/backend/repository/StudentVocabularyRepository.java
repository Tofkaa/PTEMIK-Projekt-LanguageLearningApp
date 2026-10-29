package com.languageapp.backend.repository;

import com.languageapp.backend.entity.Course;
import com.languageapp.backend.entity.StudentVocabulary;
import com.languageapp.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing student vocabulary entries.
 */
@Repository
public interface StudentVocabularyRepository extends JpaRepository<StudentVocabulary, UUID> {
    record LookupWord(java.util.UUID id,String word,String translation,int level,java.time.Instant nextPractice) { }
    @org.springframework.data.jpa.repository.Query("select new com.languageapp.backend.repository.StudentVocabularyRepository$LookupWord(v.vocabularyId,v.word,v.translation,v.srsLevel,v.nextPracticeAt) "
            + "from StudentVocabulary v where v.user.userId=:userId and v.course.courseId=:courseId and lower(v.word) in :words")
    java.util.List<LookupWord> findLookupWordSnapshots(java.util.UUID userId,java.util.UUID courseId,java.util.Collection<String> words);
    @org.springframework.data.jpa.repository.Query("select v from StudentVocabulary v where v.user.userId=:userId and v.course.courseId=:courseId and lower(v.word) in :words")
    java.util.List<StudentVocabulary> findLookupWords(java.util.UUID userId,java.util.UUID courseId,java.util.Collection<String> words);


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
    List<StudentVocabulary> findAllByUser_UserIdAndCourse_CourseIdAndNextPracticeAtLessThanEqualOrderByNextPracticeAtAsc(UUID userId, UUID courseId, Instant time);

    /**
     * Retrieves all dictionary entries for the user filtered by their active course.
     */
    List<StudentVocabulary> findAllByUserAndCourse(User user, Course course);

    List<StudentVocabulary> findAllByUser_UserIdAndCourse_CourseIdAndNextPracticeAtLessThanEqualOrderByNextPracticeAtAsc(UUID userId,UUID courseId,Instant time,org.springframework.data.domain.Pageable page);
    List<StudentVocabulary> findAllByUserAndCourse(User user,Course course,org.springframework.data.domain.Pageable page);

    long countByUser_UserIdAndNextPracticeAtLessThanEqual(UUID userId, Instant time);
    long countByUser_UserIdAndCourse_CourseIdAndNextPracticeAtLessThanEqual(UUID userId, UUID courseId, Instant time);
}