package com.languageapp.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinClassroomRequest {
    @NotBlank(message = "A meghívókód megadása kötelező.")
    private String inviteCode;
}