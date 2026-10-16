package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ClassroomResponse {
    private UUID classroomId;
    private String name;
    private String description;
    private String inviteCode;
    private String teacherName;
    private Instant createdAt;
    private int activeMemberCount;
    private UUID courseId;
    private String courseCode;
}