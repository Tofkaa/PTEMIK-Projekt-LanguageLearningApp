package com.languageapp.backend.dto.projection;

public interface ClassroomMemberStatDTO {
    String getStudentName();
    Integer getTotalCompletedLessons();
    Double getAverageScore();
}