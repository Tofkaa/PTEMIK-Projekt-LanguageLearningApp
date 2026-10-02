package com.languageapp.backend.repository;

import com.languageapp.backend.entity.LessonTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LessonTopicRepository extends JpaRepository<LessonTopic, UUID> {
    @Query(value = "SELECT t.* FROM lesson_topics t", nativeQuery = true)
    List<LessonTopic> findAllTopicsIncludingDeleted();
}