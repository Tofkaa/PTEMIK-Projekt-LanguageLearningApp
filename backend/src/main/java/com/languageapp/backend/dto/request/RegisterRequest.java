package com.languageapp.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    private String role;
}