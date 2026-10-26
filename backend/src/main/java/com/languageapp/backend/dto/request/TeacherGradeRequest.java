package com.languageapp.backend.dto.request;

import lombok.Data;

@Data
public class TeacherGradeRequest {
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100)
    private Integer teacherScore;
    @jakarta.validation.constraints.Size(max = 4000)
    private String teacherComment;
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0)
    private Long revision;
}
