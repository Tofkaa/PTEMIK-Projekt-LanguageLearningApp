package com.languageapp.backend.dto.response;

import com.languageapp.backend.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Safe Data Transfer Object for transferring user profile information.
 * Strictly excludes sensitive data such as password hashes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID userId;
    private String email;
    private String name;
    private Role role;
    private int xp;
    private int streak;
    private String preferredDifficulty;
    private String userTag;
    private String friendCode;
}