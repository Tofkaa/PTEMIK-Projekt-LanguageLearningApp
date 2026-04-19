package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClassroomStatisticsResponse {
    private double classAverage;
    private int totalAssignments;
    private List<StudentProgressDTO> studentProgress;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StudentProgressDTO {
        private String studentName;
        private String studentEmail;
        private int completedCount;
        private double averageScore;
        private String lastActivity;
    }
}