package com.languageapp.backend.dto.request;

import lombok.Data;

@Data
public class EmailChangeRequestDto {
    private String newEmail;
    private String currentPassword;
}