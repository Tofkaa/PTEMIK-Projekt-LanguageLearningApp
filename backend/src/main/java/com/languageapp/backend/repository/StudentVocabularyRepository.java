package com.languageapp.backend.repository;

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

    /**
     * Retrieves the entire saved vocabulary for a specific user, ordered by acquisition date.
     */
    List<StudentVocabulary> findAllByUser_UserIdOrderByFirstSeenAtDesc(UUID userId);

    /**
     * Checks for the existence of a word in a user's vocabulary to prevent duplicates.
     */
    Optional<StudentVocabulary> findByUser_UserIdAndWordIgnoreCase(UUID userId, String word);

    /**
     * Fetches vocabulary items scheduled for review up to the specified time.
     */
    List<StudentVocabulary> findAllByUser_UserIdAndNextPracticeAtBeforeOrderByNextPracticeAtAsc(UUID userId, LocalDateTime time);

    /**
     * Counts the number of vocabulary items currently due for review.
     */
    long countByUser_UserIdAndNextPracticeAtBefore(UUID userId, LocalDateTime time);

    /**
     * Retrieves all dictionary entries for the user.
     */
    List<StudentVocabulary> findAllByUser(User user);

    /**
     * Retrieves due (or low SRS level) words for dynamic practice.
     */
    @Query("SELECT v FROM StudentVocabulary v WHERE v.user.userId = :userId AND (v.nextPracticeAt <= :now OR v.srsLevel < 3) ORDER BY v.nextPracticeAt ASC")
    List<StudentVocabulary> findDueOrLowLevelWords(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * Counts the number of words the student has saved so far.
     */
    long countByUser_UserId(UUID userId);

    /**
     * Counts words that reach a given SRS level (or above).
     */
    long countByUser_UserIdAndSrsLevelGreaterThanEqual(UUID userId, int srsLevel);
}