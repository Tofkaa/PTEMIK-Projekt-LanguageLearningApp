package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lesson_topics")
@Data
public class LessonTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "topic_id", updatable = false, nullable = false)
    private UUID topicId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Lesson> lessons = new ArrayList<>();
}