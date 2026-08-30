package com.languageapp.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ClassroomAnalyticsResponse {
    private List<AssignmentHeaderDTO> assignmentHeaders;
    private List<StudentHeatmapRowDTO> heatmapData;
    private List<FrequentMistakeDTO> topMistakes;

    @Data
    @Builder
    public static class AssignmentHeaderDTO {
        private String assignmentId;
        private String title;
    }

    @Data
    @Builder
    public static class StudentHeatmapRowDTO {
        private String studentName;
        private String studentEmail;
        private Map<String, Integer> scores;
    }

    @Data
    @Builder
    public static class FrequentMistakeDTO {
        private String question;
        private int mistakeCount;
        private String assignmentTitle;
        private Object exercise;
        private String serverCorrectAnswer;
    }
}