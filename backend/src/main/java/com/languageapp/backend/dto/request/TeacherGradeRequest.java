package com.languageapp.backend.dto.request;

import lombok.Data;

@Data
public class TeacherGradeRequest {
    private Integer teacherScore;
    private String teacherComment;
}