package com.languageapp.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;  // A rövid lejáratú JWT token
    // A refresh token nem kerül ide, mert azt HttpOnly Cookie-ban küldjük
    private UUID userId;
    private String name;
    private String email;
    private String role;
}