package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "course_id", updatable = false, nullable = false)
    private UUID courseId;

    @Column(name = "language_code", nullable = false, unique = true, length = 10)
    private String languageCode; // eg.: "en", "es", "de"

    @Column(name = "language_name", nullable = false, length = 50)
    private String languageName; // eg.: "English", "Spanish", "German"

    @Column(name = "flag_url", columnDefinition = "TEXT")
    private String flagUrl;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}