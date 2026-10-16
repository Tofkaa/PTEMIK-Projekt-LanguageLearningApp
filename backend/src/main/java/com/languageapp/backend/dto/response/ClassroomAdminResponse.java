package com.languageapp.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ClassroomAdminResponse {
    private UUID classroomId;
    private String name;
    private String description;
    private String inviteCode;
    private Instant createdAt;
    private boolean isActive;

    private String teacherName;
    private String teacherEmail;
}