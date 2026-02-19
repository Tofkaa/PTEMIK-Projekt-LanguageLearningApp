package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lessons")
@Data
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "lesson_id", updatable = false, nullable = false)
    private UUID lessonId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private LessonTopic topic;
    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Exercise> exercises = new ArrayList<>();

    @Column(nullable = false)
    private String title;

    @Column(length = 50)
    private String difficulty;

    @Column(length = 10)
    private String language;

    @Column(columnDefinition = "TEXT")
    private String description;
}