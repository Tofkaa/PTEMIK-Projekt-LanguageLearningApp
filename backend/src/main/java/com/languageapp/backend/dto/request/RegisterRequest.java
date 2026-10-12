package com.languageapp.backend.dto.request;

import com.languageapp.backend.enums.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Name can not be empty!")
    private String name;

    @NotBlank(message = "The email can not be empty!")
    @Email(message = "Invalid email format!")
    private String email;

    @NotBlank(message = "The password can not be empty!")
    @Size(min = 8, message = "The password has to be at least 8 characters in length!")
    private String password;
    private String preferredDifficulty;

    @NotNull(message = "Role must be specified")
    private Role role;

    private String courseCode;

    @NotNull(message = "Az ÁSZF elfogadása kötelező!")
    @AssertTrue(message = "Az ÁSZF elfogadása kötelező!")
    private Boolean acceptTerms;
}