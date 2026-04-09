package com.languageapp.backend.dto.response;

import com.languageapp.backend.enums.MembershipStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ClassroomMemberResponse {
    private UUID memberId;
    private UUID userId;
    private String studentName;
    private String studentEmail;
    private String userTag;
    private MembershipStatus status;
    private LocalDateTime joinedAt;
}