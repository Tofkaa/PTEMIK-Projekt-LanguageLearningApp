package com.languageapp.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "The email can not be empty!")
    @Email(message = "Invalid email format!")
    private String email;

    @NotBlank(message = "The password can not be empty!")
    private String password;
    private boolean rememberMe;
}