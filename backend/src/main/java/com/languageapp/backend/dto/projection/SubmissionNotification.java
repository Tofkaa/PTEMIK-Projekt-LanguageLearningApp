package com.languageapp.backend.dto.projection;

import java.util.UUID;

public record SubmissionNotification(UUID classroomId, UUID assignmentId, UUID sessionId) {
    public String key() { return classroomId + ":" + assignmentId + ":" + sessionId; }
}
