package com.languageapp.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClassroomCreateRequest {
    @NotBlank(message = "Az osztályterem neve nem lehet üres.")
    @Size(max = 255, message = "A név maximum 255 karakter lehet.")
    private String name;

    private String description;
}