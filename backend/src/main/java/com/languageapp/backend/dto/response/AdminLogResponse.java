package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class AdminLogResponse {
    private UUID logId;
    private AdminUserDto admin;
    private String actionType;
    private String details;
    private LocalDateTime loggedAt;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AdminUserDto {
        private String name;
        private String email;
    }
}