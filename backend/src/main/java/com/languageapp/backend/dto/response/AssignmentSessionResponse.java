package com.languageapp.backend.dto.response;

import com.languageapp.backend.dto.request.ExerciseSubmission; // Importáld be a submission DTO-t
import com.languageapp.backend.entity.Exercise;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class AssignmentSessionResponse {
    private UUID sessionId;
    private String studentName;
    private String studentEmail;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer finalScore;
    private Integer teacherScore;
    private String teacherComment;
    private boolean isGraded;

    private List<AnswerDetail> answers;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AnswerDetail {
        private String question;
        private String studentAnswer;
        private boolean correct;
        private boolean retried;
        private Exercise exercise;
        private String serverCorrectAnswer;
    }
}