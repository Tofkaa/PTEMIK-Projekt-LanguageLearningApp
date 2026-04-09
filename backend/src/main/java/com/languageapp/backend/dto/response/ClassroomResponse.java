package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ClassroomResponse {
    private UUID classroomId;
    private String name;
    private String description;
    private String inviteCode;
    private String teacherName;
    private LocalDateTime createdAt;
    private int activeMemberCount; //Optinal for later

}