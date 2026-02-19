package com.languageapp.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "achievements")
@Data
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "achievement_id", updatable = false, nullable = false)
    private UUID achievementId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> criteria;

    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;
}