package com.languageapp.backend.dto.projection;

public interface LessonPerformanceDTO {
    String getLessonTitle();
    String getDifficulty();
    Integer getCompletionCount();
    Double getAverageTimeTaken();
}