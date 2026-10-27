package com.languageapp.backend.dto.projection;

import java.util.UUID;

public record AssignmentNotification(UUID classroomId, UUID assignmentId) {
    public String key() { return classroomId + ":" + assignmentId; }
}
