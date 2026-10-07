package com.languageapp.backend.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class TopicImportRequest {
    private String courseCode;
    private String topicName;
    private String description;
    private List<LessonImportRequest> lessons;
}