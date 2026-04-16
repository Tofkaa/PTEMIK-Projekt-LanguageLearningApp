package com.languageapp.backend.dto.response;

import com.languageapp.backend.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;  // Short-lived JWT access token
    // The refresh token does not get placed here, as we send it within a HttpOnly Cookie
    private UUID userId;
    private String name;
    private String email;
    private Role role;
    private String userTag;
    private String friendCode;
}