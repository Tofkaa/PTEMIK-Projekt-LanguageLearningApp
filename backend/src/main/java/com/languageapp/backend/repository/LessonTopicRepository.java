package com.languageapp.backend.repository;

import com.languageapp.backend.entity.LessonTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LessonTopicRepository extends JpaRepository<LessonTopic, UUID> {
}