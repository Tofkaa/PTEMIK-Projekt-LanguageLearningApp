package com.languageapp.backend.dto.response;

import com.languageapp.backend.entity.Exercise;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO provided when a student initiates or resumes an assignment session.
 * Includes the session ID and server-side start time for synchronized timing.
 */
@Data
@AllArgsConstructor
public class AssignmentStartResponse {
    private UUID sessionId;
    private Instant startedAt;
    private Integer timeLimitMinutes;
    private boolean allowRetries;
    private boolean hasFeedback;
    private List<Exercise> exercises;
    private UUID classroomId;
    private AssignmentTimingResponse timing;
    private UUID courseId;
    private String courseCode;
}
