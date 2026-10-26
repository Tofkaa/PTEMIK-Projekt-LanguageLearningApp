package com.languageapp.backend.dto.projection;

import java.util.UUID;

public record PendingJoinNotification(UUID classroomId, UUID userId) {
    public String key() { return classroomId + ":" + userId; }
}
